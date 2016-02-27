package com.hoho.android.usbserial;

/**
 * Created by User on 18.05.2015.
 */

public final class BuildInfo {

    /**
     * The current version of this library. Values are of the form
     * "major.minor.micro[-suffix]". A suffix of "-pre" indicates a pre-release
     * of the version preceeding it.
     */
    public static final String VERSION = "0.2.0-pre";

    private BuildInfo() {
        throw new IllegalStateException("Non-instantiable class.");
    }

}
