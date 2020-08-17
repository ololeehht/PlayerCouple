package com.qh.mplayer.utils;

import android.util.Log;

import org.videolan.vlc.plugin.api.BuildConfig;

import java.util.Arrays;

public class LogUtils {
    public static final String TAG="==QH==";


    public static  void logd(String content){
       if(BuildConfig.DEBUG)
       {
           Log.d(TAG, "===="+content);
       }
    }

    public static  void logd(Integer content){
        if(BuildConfig.DEBUG)
        {
            Log.d(TAG, "===="+content);
        }
    }

    public static  void logd(Double content){
        if(BuildConfig.DEBUG)
        {
            Log.d(TAG, "===="+content);
        }
    }


    public static  void logd(Float content){
        if(BuildConfig.DEBUG)
        {
            Log.d(TAG, "===="+content);
        }
    }

    public static  void logd(Byte content){
        if(BuildConfig.DEBUG)
        {
            Log.d(TAG, "===="+content);
        }
    }

    public static  void logd(String... content){
        if(BuildConfig.DEBUG)
        {
            Log.d(TAG, "===="+ Arrays.toString(content));
        }
    }


    public static  void loge(String content){
        if(BuildConfig.DEBUG)
        {
            Log.e(TAG, "===="+content);
        }
    }

    public static  void loge(Integer content){
        if(BuildConfig.DEBUG)
        {
            Log.e(TAG, "===="+content);
        }
    }

    public static  void loge(double content){
        if(BuildConfig.DEBUG)
        {
            Log.e(TAG, "===="+content);
        }
    }


    public static  void loge(Float content){
        if(BuildConfig.DEBUG)
        {
            Log.e(TAG, "===="+content);
        }
    }

    public static  void loge(Byte content){
        if(BuildConfig.DEBUG)
        {
            Log.e(TAG, "===="+content);
        }
    }

    public static  void loge(String... content){
        if(BuildConfig.DEBUG)
        {
            Log.e(TAG, "===="+ Arrays.toString(content));
        }
    }
}
