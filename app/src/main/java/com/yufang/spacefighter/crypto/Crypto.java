/*
* Copyright 2015 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.yufang.spacefighter.crypto;

import android.content.Context;
import android.media.MediaDrm;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.os.Looper;
import android.util.Pair;

// import com.yufang.spacefighter.logger.Log;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

public class Crypto {

    public static final String TAG = "Crypto";

    // pssh to get all keys needed for encrypt, decrypt, sign & verify test
    private static final byte[] GENERIC_OPS_PSSH =
            hex2ba("080112303be2b25db355fc64a0e69a50f4dbb2982685086ee9c"
                    + "b5835b063ab20786ffd7897c003f73b1a53aa51ba54a6ef631ca0");

    private static final String OPERATOR_SESSION_KEY_SERVER_URL =
            "http://widevine-proxy.appspot.com/proxy";

    private static final UUID WIDEVINE_SCHEME = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

    private static byte[] sKeySetId = null;

    private int mDataLength = 0;
    private MediaDrm mDrm = null;
    private Looper mLooper;
    private final Object mLock = new Object();

    public void logBytes(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for(byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        Log.i(TAG, "Bytes: " + builder.toString());
    }

    /**
     * Converts hexidecimal string to byte array.
     *
     * @param s hex string to convert
     * @return byte array of hex string
     */
    public static byte[] hex2ba(final String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Starts drm thread.
     *
     * @param scheme DRM scheme
     * @return DRM object
     */
    public MediaDrm startDrm(final UUID scheme) {

        new Thread() {
            @Override
            public void run() {
                // Set up a looper to handle events
                Looper.prepare();

                // Save the looper so that we can terminate this thread
                // after we are done with it.
                mLooper = Looper.myLooper();

                try {
                    mDrm = new MediaDrm(scheme);
                } catch (MediaDrmException e) {
                    Log.e(TAG, "error: " + e.getMessage());
                    return;
                }

                synchronized (mLock) {
                    mDrm.setOnEventListener(new MediaDrm.OnEventListener() {
                        @SuppressWarnings("deprecation")
                        @Override
                        public void onEvent(MediaDrm md, byte[] sessionId, int event,
                                            int extra, byte[] data) {
                            if (event == MediaDrm.EVENT_PROVISION_REQUIRED) {
                                Log.i(TAG, "Provisioning is required");
                            } else if (event == MediaDrm.EVENT_KEY_REQUIRED) {
                                Log.i(TAG, "MediaDrm event: Key required");
                            } else if (event == MediaDrm.EVENT_KEY_EXPIRED) {
                                Log.i(TAG, "MediaDrm event: Key expired");
                                // Do nothing.
                            } else if (event == MediaDrm.EVENT_VENDOR_DEFINED) {
                                Log.i(TAG, "MediaDrm event: Vendor defined: " + event);
                            }
                        }
                    });
                    mLock.notify();
                }

                Looper.loop();  // Blocks forever until Looper.quit() is called.
            }
        }.start();

        // wait for mDrm to be created
        synchronized (mLock) {
            try {
                mLock.wait(1000);
            } catch (Exception e) {
                Log.e(TAG, "Exceeds wait time for drm creation");
            }
        }

        return mDrm;
    }

    /**
     * Stops DRM thread.
     *
     * @param drm Drm instance to stop
     */
    public void stopDrm(MediaDrm drm) {
        if (drm != mDrm) {
            Log.e(TAG, "invalid drm specified in stopDrm");
        }
        mLooper.quit();
    }

    /**
     * Executes a post request using {@link HttpURLConnection}.
     *
     * @param url               The request URL.
     * @param data              The request body, or null.
     * @param requestProperties Request properties, or null.
     * @return The response code and body.
     * @throws IOException If an error occurred making the request.
     */
    public static Pair<Integer, byte[]> executePost(
            String url, byte[] data,
            Map<String, String> requestProperties) throws IOException {
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) new URL(url).openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(data != null);
            urlConnection.setDoInput(true);
            urlConnection.setConnectTimeout(6000);
            urlConnection.setReadTimeout(6000);
            if (requestProperties != null) {
                for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
                    urlConnection.setRequestProperty(requestProperty.getKey(),
                            requestProperty.getValue());
                }
            }
            // Write the request body, if there is one.
            if (data != null) {
                try (OutputStream out = urlConnection.getOutputStream()) {
                    out.write(data);
                }
            /*
                OutputStream out = urlConnection.getOutputStream();
                try {
                    out.write(data);
                } finally {
                    out.close();
                }
                */
            }
            // Read the response code.
            int responseCode = urlConnection.getResponseCode();
            try (InputStream inputStream = urlConnection.getInputStream()) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte scratch[] = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(scratch)) != -1) {
                    byteArrayOutputStream.write(scratch, 0, bytesRead);
                }
                byte[] responseBody = byteArrayOutputStream.toByteArray();
                Log.d(TAG, "responseCode=" + responseCode + ", length=" + responseBody.length);
                return Pair.create(responseCode, responseBody);
            }
            /*
            // Read the response body.
            InputStream inputStream = urlConnection.getInputStream();
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte scratch[] = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(scratch)) != -1) {
                    byteArrayOutputStream.write(scratch, 0, bytesRead);
                }
                byte[] responseBody = byteArrayOutputStream.toByteArray();
                Log.d(TAG, "responseCode=" + responseCode + ", length=" + responseBody.length);
                return Pair.create(responseCode, responseBody);
            } finally {
                inputStream.close();
            }
            */
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    /**
     * Opens Drm session.
     *
     * @param drm   Opens session for the drm.
     * @return session id
     */
    public byte[] openSession(MediaDrm drm) {
        byte[] sessionId = null;
        int retryCount = 3;
        while (--retryCount > 0) {
            try {
                sessionId = drm.openSession();
                break;
            } catch (NotProvisionedException e) {
                Log.i(TAG, "Missing certificate, provisioning");
                ProvisionRequester provisionRequester = new ProvisionRequester();
                provisionRequester.doTransact(drm);
            } catch (ResourceBusyException e) {
                Log.w(TAG, "Resource busy in openSession, retrying...");
                sleep(1000);
            }
        }

        if (retryCount == 0) {
            Log.e(TAG, "Failed to provision device");
            // return null sessionId
        }
        return sessionId;
    }

    /**
     * Closes Drm session.
     *
     * @param drm       Closes the drm session
     * @param sessionId Session Id
     */
    public void closeSession(MediaDrm drm, final byte[] sessionId) {
        drm.closeSession(sessionId);
    }

    /**
     * Initializes crypto operations.
     */
    public void init() {
        mDrm = startDrm(WIDEVINE_SCHEME);
        mDataLength = 0;
    }

    /**
     * Cleans up crypto operations.
     */
    public void close() {
        stopDrm(mDrm);
    }

    /**
     * Returns data size.
     *
     * @return length of data
     */
    final public int getDataLength() {
        return mDataLength;
    }

    public byte[] decryptResource(Context context, InputStream inputStream, final long length) {
        // Log.e(TAG, "length: " + length);
        int paddedLength = ((int) length + 15) & ~15;
        byte[] data = new byte[(int) paddedLength];

        if (null == mDrm) {
            Log.e(TAG, "null Drm object");
            return data;
        }

        byte[] sessionId = openSession(mDrm);

        KeyRequester keyRequester = new KeyRequester(
                GENERIC_OPS_PSSH, OPERATOR_SESSION_KEY_SERVER_URL);

        if (null == sKeySetId) {
            sKeySetId = keyRequester.doTransact(mDrm, sessionId, MediaDrm.KEY_TYPE_OFFLINE);
            Log.i(TAG, "sKeySetId is null");
        } else {
            mDrm.restoreKeys(sessionId, sKeySetId);
            Log.i(TAG, "sKeySetId is not null");
        }

        MediaDrm.CryptoSession cs = mDrm.getCryptoSession(sessionId, "AES/CBC/NoPadding", "HmacSHA256");

        // operator_session_key_permissions = allow_encrypt | allow_decrypt
        byte[] aes_key_id = hex2ba("3be2b25db355fc64a0e69a50f4dbb298");
        byte[] iv = hex2ba("3ec0f3d3970fbd541ac4e7e1d06a6131");

        try {
            inputStream.read(data, 0, (int) length);
        } catch (IndexOutOfBoundsException | IOException ei) {
            Log.e(TAG, "Resource read error: " + ei.getMessage());
        }

        //[ew] data len=73836, 73840
// Use the code below to encrypt file.
/*
        byte[] encData = {0};
        if (data.length > 0) {
            encData = cs.encrypt(aes_key_id, data, iv);
            if (encData.length <= 0) {
                Log.d(TAG, "!!!ewew Fail to encrypt");
            }
        } else {
            Log.d(TAG, "!!!ewew Fail to load resource");
        }
        Log.d(TAG, "!!!ewew enc data length=" + encData.length);
// Output file can be found by going Tool -> Android -> Android Device Monitor -> File Manager
// data -> data -> com.yufang.spacefighter -> files
        // write encrypted file
        final String encFileName = "player_enc.bin";
        FileOutputStream encFileStream;
        try {
            encFileStream = context.openFileOutput(encFileName, context.MODE_PRIVATE);
            encFileStream.write(encData);
            encFileStream.close();
        } catch (Exception efos) {
            efos.getMessage();
        }
 */

        byte[] clearData = cs.decrypt(aes_key_id, data, iv);

        mDataLength = clearData.length;

        closeSession(mDrm, sessionId);
        return clearData;
    }

    /**
     * Performs thread sleep.
     *
     * @param msec milliseconds
     */
    private void sleep(int msec) {
        try {
            Thread.sleep(msec);
        } catch (InterruptedException e) {
            // Do nothing
        }
    }
}

