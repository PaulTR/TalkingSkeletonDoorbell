package com.tutsplus.smartskeleton;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.things.contrib.driver.pwmservo.Servo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

public class MainActivity extends Activity implements ImageReader.OnImageAvailableListener, HCSR501.OnMotionDetectedEventListener {

    private static final String FIREBASE_URL = "gs://androidthings-588b0.appspot.com";

    private Handler mCameraBackgroundHandler;
    private HandlerThread mCameraBackgroundThread;

    private Handler mServoHandler;

    private DoorbellCamera mCamera;
    private HCSR501 mMotionSensor;
    private Servo mServo;

    private TextToSpeech textToSpeech;

    private final int MAX_MOUTH_MOVEMENT = 6;
    int mouthCounter = MAX_MOUTH_MOVEMENT;

    private Runnable mMoveServoRunnable = new Runnable() {

        private static final long DELAY_MS = 1000L; // 5 seconds

        private double mAngle = Float.NEGATIVE_INFINITY;

        @Override
        public void run() {
            if (mServo == null || mouthCounter <= 0) {
                return;
            }

            try {
                if (mAngle <= mServo.getMinimumAngle()) {
                    mAngle = mServo.getMaximumAngle();
                } else {
                    mAngle = mServo.getMinimumAngle();
                }
                mServo.setAngle(mAngle);

                mouthCounter--;
                mServoHandler.postDelayed(this, DELAY_MS);
            } catch (IOException e) {
            }
        }
    };

    private UtteranceProgressListener utteranceListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {

        }

        @Override
        public void onDone(String utteranceId) {

        }

        @Override
        public void onError(String utteranceId) {

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseApp.initializeApp(this);
        initServo();
        initMotionDetection();
        initCamera();
        initTextToSpeech();
    }

    private void initTextToSpeech() {

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech.setLanguage(Locale.UK);
                    textToSpeech.setOnUtteranceProgressListener(utteranceListener);
                    textToSpeech.setPitch(0.3f);
                } else {
                    textToSpeech = null;
                }
            }
        });

    }

    private void initCamera() {
        mCameraBackgroundThread = new HandlerThread("CameraInputThread");
        mCameraBackgroundThread.start();
        mCameraBackgroundHandler = new Handler(mCameraBackgroundThread.getLooper());

        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(this, mCameraBackgroundHandler, this);
    }

    private void initServo() {
        try {
            mServo = new Servo(BoardDefaults.getServoPwmPin());
            mServo.setAngleRange(0f, 180f);
            mServo.setEnabled(true);
        } catch (IOException e) {
            Log.e("Camera App", e.getMessage());
            return; // don't init handler. Stuff broke.
        }
    }

    private void initMotionDetection() {
        try {
            mMotionSensor = new HCSR501(BoardDefaults.getMotionDetectorPin());
            mMotionSensor.setOnMotionDetectedEventListener(this);
        } catch (IOException e) {

        }
    }

    private void onPictureTaken(byte[] imageBytes) {
        if (imageBytes != null) {
            FirebaseStorage storage = FirebaseStorage.getInstance();

            StorageReference storageReference = storage.getReferenceFromUrl(FIREBASE_URL).child(System.currentTimeMillis() + ".png");

            UploadTask uploadTask = storageReference.putBytes(imageBytes);
            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                }
            });

        }
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Image image = imageReader.acquireLatestImage();
        ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
        final byte[] imageBytes = new byte[imageBuf.remaining()];
        imageBuf.get(imageBytes);
        image.close();

        onPictureTaken(imageBytes);
    }

    private void moveMouth() {
        if (mServoHandler != null) {
            mServoHandler.removeCallbacks(mMoveServoRunnable);
        }

        mouthCounter = MAX_MOUTH_MOVEMENT;
        mServoHandler = new Handler();
        mServoHandler.post(mMoveServoRunnable);
    }

    @Override
    public void onMotionDetectedEvent(HCSR501.State state) {
        if (state == HCSR501.State.STATE_HIGH) {
            mCamera.takePicture();
            moveMouth();

            textToSpeech.speak("Thanks for stopping by!", TextToSpeech.QUEUE_ADD, null, "skeletontts");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mMotionSensor != null) {
            try {
                mMotionSensor.close();
            } catch (IOException e) {

            } finally {
                mMotionSensor = null;
            }
        }

        if (mServoHandler != null) {
            mServoHandler.removeCallbacks(mMoveServoRunnable);
        }
        if (mServo != null) {
            try {
                mServo.close();
            } catch (IOException e) {
            } finally {
                mServo = null;
            }
        }

        mCameraBackgroundThread.quitSafely();
        mCamera.shutDown();
    }

}
