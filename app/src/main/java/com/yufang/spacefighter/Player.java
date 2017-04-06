package com.yufang.spacefighter;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import com.yufang.spacefighter.crypto.Crypto;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class Player {
    private Bitmap bitmap;
    private int x;
    private int y;
    private int speed = 0;
    private boolean boosting;
    private final int GRAVITY = -10;
    private int maxY;
    private int minY;

    private final int MIN_SPEED = 1;
    private final int MAX_SPEED = 20;

    private Rect detectCollision;

    private Crypto mCrypto = new Crypto();

    /* (1) Invalid/Encrypted png file seems to cause issue if you put it under drawable
       (2) Try to BitmapFactory.decodeByteArray from an invalid/encrypted file. You will get
        caused by: java.lang.NullPointerException: Attempt to invoke virtual method 'int android.graphics.Bitmap.getHeight()' on a null object reference
     */
    public Player(Context context, int screenX, int screenY) {
        x = 75;
        y = 50;
        speed = 1;
        // bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.player);

        // "player_encrypted" cause fatal
        // "player_decrypted"
/*
        InputStream ins = context.getResources().openRawResource(
                context.getResources().getIdentifier("player_decrypted",
                        "raw", context.getPackageName()));
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int data = 0;
        int size = 0;
        try {
            data = ins.read(); // read first byte
            while (data != -1)
            {
                size=size + 1;
                byteArrayOutputStream.write(data);
                data = ins.read(); // read next byte
            }
            ins.close();
        } catch (IOException e) {
            return;
        }
        System.out.println("size: " + size);
*/
        // Someone bitmap created from decodeByteArray() is smaller than decodeResource().
        // bitmap = BitmapFactory.decodeByteArray(byteArrayOutputStream.toByteArray(),0,size);
        // bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.player);

        ///*
// DRM!!!!
        AssetFileDescriptor afd = context.getResources().openRawResourceFd(R.raw.player_enc);
        mCrypto.init();
        bitmap = BitmapFactory.decodeByteArray(
                mCrypto.decryptResource(
                        context,
                        context.getResources().openRawResource(R.raw.player_enc), afd.getLength()),
                0, mCrypto.getDataLength());
// DRM!!!!
        //*/

        maxY = screenY - bitmap.getHeight();
        minY = 0;
        boosting = false;

        //initializing rect object
        detectCollision =  new Rect(x, y, bitmap.getWidth(), bitmap.getHeight());
    }

    public void setBoosting() {
        boosting = true;
    }

    public void stopBoosting() {
        boosting = false;
    }

    public void update() {
        if (boosting) {
            speed += 2;
        } else {
            speed -= 5;
        }

        if (speed > MAX_SPEED) {
            speed = MAX_SPEED;
        }

        if (speed < MIN_SPEED) {
            speed = MIN_SPEED;
        }

        y -= speed + GRAVITY;

        if (y < minY) {
            y = minY;
        }
        if (y > maxY) {
            y = maxY;
        }

        //adding top, left, bottom and right to the rect object
        detectCollision.left = x;
        detectCollision.top = y;
        detectCollision.right = x + bitmap.getWidth();
        detectCollision.bottom = y + bitmap.getHeight();

    }

    //one more getter for getting the rect object
    public Rect getDetectCollision() {
        return detectCollision;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getSpeed() {
        return speed;
    }
}