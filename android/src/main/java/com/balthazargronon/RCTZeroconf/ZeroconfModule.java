package com.balthazargronon.RCTZeroconf;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import javax.annotation.Nullable;

import java.util.Locale;
import java.util.Map;
import java.io.UnsupportedEncodingException;

/**
 * Created by Jeremy White on 8/1/2016.
 * Copyright © 2016 Balthazar Gronon MIT
 */
public class ZeroconfModule extends ReactContextBaseJavaModule {

    public static final String EVENT_START = "RNZeroconfStart";
    public static final String EVENT_STOP = "RNZeroconfStop";
    public static final String EVENT_ERROR = "RNZeroconfError";
    public static final String EVENT_FOUND = "RNZeroconfFound";
    public static final String EVENT_REMOVE = "RNZeroconfRemove";
    public static final String EVENT_RESOLVE = "RNZeroconfResolved";

    public static final String KEY_SERVICE_NAME = "name";
    public static final String KEY_SERVICE_FULL_NAME = "fullName";
    public static final String KEY_SERVICE_HOST = "host";
    public static final String KEY_SERVICE_PORT = "port";
    public static final String KEY_SERVICE_ADDRESSES = "addresses";
    public static final String KEY_SERVICE_TXT = "txt";

    protected NsdManager mNsdManager;
    protected NsdManager.DiscoveryListener mDiscoveryListener;

    public ZeroconfModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "RNZeroconf";
    }

    @ReactMethod
    public void scan(String type, String protocol, String domain) {
        if (mNsdManager == null) {
            mNsdManager = (NsdManager) getReactApplicationContext().getSystemService(Context.NSD_SERVICE);
        }

        this.stop();

        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                String error = "Starting service discovery failed with code: " + errorCode;
                sendEvent(getReactApplicationContext(), EVENT_ERROR, null, error);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                String error = "Stopping service discovery failed with code: " + errorCode;
                sendEvent(getReactApplicationContext(), EVENT_ERROR, null, error);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                sendEvent(getReactApplicationContext(), EVENT_START, null, null);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                sendEvent(getReactApplicationContext(), EVENT_STOP, null, null);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                WritableMap service = new WritableNativeMap();
                service.putString(KEY_SERVICE_NAME, serviceInfo.getServiceName());

                sendEvent(getReactApplicationContext(), EVENT_FOUND, service, null);
                mNsdManager.resolveService(serviceInfo, new ZeroResolveListener());
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                WritableMap service = new WritableNativeMap();
                service.putString(KEY_SERVICE_NAME, serviceInfo.getServiceName());
                sendEvent(getReactApplicationContext(), EVENT_REMOVE, service, null);
            }
        };

        String serviceType = String.format("_%s._%s.", type, protocol);
        mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    @ReactMethod
    public void stop() {
        if (mDiscoveryListener != null) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        mDiscoveryListener = null;
    }

    protected void sendEvent(ReactContext reactContext,
                             String eventName,
                             @Nullable Object params,
                             @Nullable String errorString
                             ) {
        if (errorString == null){
                    reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        }else{
            WritableMap payload = new WritableNativeMap();
            // Put data to map
            payload.putString("error", errorString);
            reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, payload);
        }

    }

    private class ZeroResolveListener implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                mNsdManager.resolveService(serviceInfo, this);
            } else {
                String error = "Resolving service failed with code: " + errorCode;
                sendEvent(getReactApplicationContext(), EVENT_ERROR, null, error);
            }
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            WritableMap service = new WritableNativeMap();
            service.putString(KEY_SERVICE_NAME, serviceInfo.getServiceName());
            service.putString(KEY_SERVICE_FULL_NAME, serviceInfo.getHost().getHostName() + serviceInfo.getServiceType());
            service.putString(KEY_SERVICE_HOST, serviceInfo.getHost().getHostName());
            service.putInt(KEY_SERVICE_PORT, serviceInfo.getPort());

            WritableMap txtRecords = new WritableNativeMap();

            Map<String, byte[]> attributes = serviceInfo.getAttributes();
            for (String key : attributes.keySet()) {
              try {
                byte[] recordValue = attributes.get(key);
                txtRecords.putString(String.format(Locale.getDefault(), "%s", key), String.format(Locale.getDefault(), "%s", recordValue != null ? new String(recordValue, "UTF_8") : ""));
              } catch (UnsupportedEncodingException e) {
                String error = "Failed to encode txtRecord: " + e;
                sendEvent(getReactApplicationContext(), EVENT_ERROR, null, error);
              }
            }

            service.putMap(KEY_SERVICE_TXT, txtRecords);

            WritableArray addresses = new WritableNativeArray();
            addresses.pushString(serviceInfo.getHost().getHostAddress());
            
            if (serviceInfo.getHost().getHostAddress().equals("")) { 
                String notIpErrorString = "Ip not resolved";
                sendEvent(getReactApplicationContext(), EVENT_ERROR, null, notIpErrorString);
                mNsdManager.resolveService(serviceInfo, this);
                return;
            }

            service.putArray(KEY_SERVICE_ADDRESSES, addresses);

            sendEvent(getReactApplicationContext(), EVENT_RESOLVE, service, null);
        }
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        stop();
    }
}
