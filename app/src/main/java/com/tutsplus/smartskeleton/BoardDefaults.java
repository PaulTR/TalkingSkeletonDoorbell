package com.tutsplus.smartskeleton;

import android.os.Build;

import com.google.android.things.pio.PeripheralManagerService;

import java.util.List;

@SuppressWarnings("WeakerAccess")
public class BoardDefaults {
    public static String getMotionDetectorPin() {
        return "BCM21";
    }

    public static String getServoPwmPin() {
        return "PWM1";
    }
}