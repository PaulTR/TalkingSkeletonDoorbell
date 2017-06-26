package com.tutsplus.smartskeleton;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

@SuppressWarnings({"unused", "WeakerAccess"})
public class HCSR501 implements AutoCloseable {

    public enum State {
        STATE_HIGH,
        STATE_LOW;
    }

    public interface OnMotionDetectedEventListener {
        void onMotionDetectedEvent(State state);
    }

    private Gpio mMotionDetectorGpio;
    private OnMotionDetectedEventListener mOnMotionDetectedEventListener;

    private boolean mLastState;

    public HCSR501(String pin) throws IOException {
        PeripheralManagerService pioService = new PeripheralManagerService();

        Gpio HCSR501Gpio = pioService.openGpio(pin);

        try {
            connect(HCSR501Gpio);
        } catch( IOException | RuntimeException e ) {
            close();
            throw e;
        }
    }

    private void connect(Gpio HCSR501Gpio) throws IOException {
        mMotionDetectorGpio = HCSR501Gpio;
        mMotionDetectorGpio.setDirection(Gpio.DIRECTION_IN);
        mMotionDetectorGpio.setEdgeTriggerType(Gpio.EDGE_BOTH);

        mLastState = mMotionDetectorGpio.getValue();

        mMotionDetectorGpio.setActiveType(mLastState ? Gpio.ACTIVE_HIGH : Gpio.ACTIVE_LOW);

        mMotionDetectorGpio.registerGpioCallback(mInterruptCallback);
    }

    private void performMotionEvent(State state) {
        if( mOnMotionDetectedEventListener != null ) {
            mOnMotionDetectedEventListener.onMotionDetectedEvent(state);
        }
    }

    private GpioCallback mInterruptCallback = new GpioCallback() {

        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                if( gpio.getValue() != mLastState ) {
                    mLastState = gpio.getValue();
                    performMotionEvent(mLastState ? State.STATE_HIGH : State.STATE_LOW);
                }

            } catch( IOException e ) {

            }

            return true;
        }
    };

    public void setOnMotionDetectedEventListener(OnMotionDetectedEventListener listener) {
        mOnMotionDetectedEventListener = listener;
    }

    @Override
    public void close() throws IOException {
        mOnMotionDetectedEventListener = null;

        if (mMotionDetectorGpio != null) {
            mMotionDetectorGpio.unregisterGpioCallback(mInterruptCallback);
            try {
                mMotionDetectorGpio.close();
            } finally {
                mMotionDetectorGpio = null;
            }
        }
    }

}