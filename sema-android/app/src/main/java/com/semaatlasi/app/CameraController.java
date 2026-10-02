package com.semaatlasi.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Collections;

public class CameraController {
    public interface Callback { void onCameraState(boolean on, String message); }

    private final Activity activity;
    private final TextureView textureView;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private String cameraId;
    private Size previewSize;
    private boolean requested;

    public CameraController(Activity activity, TextureView textureView, Callback callback) {
        this.activity=activity;
        this.textureView=textureView;
        this.callback=callback;
    }

    public boolean isRunning(){ return cameraDevice!=null || requested; }

    public void start() {
        if (activity.checkSelfPermission(Manifest.permission.CAMERA)!= PackageManager.PERMISSION_GRANTED) {
            callback.onCameraState(false,"Kamera izni gerekli");
            return;
        }
        requested=true;
        try {
            CameraManager cm=(CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
            chooseCamera(cm);
            if (cameraId==null) { requested=false; callback.onCameraState(false,"Arka kamera bulunamadı"); return; }
            if (textureView.isAvailable()) open(cm);
            else textureView.setSurfaceTextureListener(surfaceListener);
        } catch (Exception e) {
            requested=false; callback.onCameraState(false,"Kamera açılamadı");
        }
    }

    private void chooseCamera(CameraManager cm) throws Exception {
        for (String id:cm.getCameraIdList()) {
            CameraCharacteristics ch=cm.getCameraCharacteristics(id);
            Integer facing=ch.get(CameraCharacteristics.LENS_FACING);
            if (facing!=null && facing==CameraCharacteristics.LENS_FACING_BACK) {
                cameraId=id;
                android.hardware.camera2.params.StreamConfigurationMap map=
                        ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map!=null) {
                    Size[] sizes=map.getOutputSizes(SurfaceTexture.class);
                    if (sizes!=null && sizes.length>0) {
                        previewSize=sizes[0];
                        for (Size s:sizes) {
                            if (s.getWidth()<=1920 && s.getHeight()<=1080
                                    && s.getWidth()*s.getHeight()>previewSize.getWidth()*previewSize.getHeight()) previewSize=s;
                        }
                    }
                }
                return;
            }
        }
    }

    private final TextureView.SurfaceTextureListener surfaceListener=new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height) {
            try { open((CameraManager)activity.getSystemService(Context.CAMERA_SERVICE)); } catch (Exception ignored) {}
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height) {}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){ return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private void open(CameraManager cm) throws Exception {
        if (!requested || cameraDevice!=null) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) return;
        cm.openCamera(cameraId,new CameraDevice.StateCallback() {
            @Override public void onOpened(CameraDevice camera) {
                cameraDevice=camera;
                requested=false;
                createPreview();
            }
            @Override public void onDisconnected(CameraDevice camera){ camera.close(); cameraDevice=null;requested=false;callback.onCameraState(false,"Kamera bağlantısı kesildi"); }
            @Override public void onError(CameraDevice camera,int error){ camera.close();cameraDevice=null;requested=false;callback.onCameraState(false,"Kamera hatası"); }
        },handler);
    }

    private void createPreview() {
        try {
            SurfaceTexture st=textureView.getSurfaceTexture();
            if (st==null||cameraDevice==null) return;
            if (previewSize!=null) st.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());
            Surface surface=new Surface(st);
            CaptureRequest.Builder builder=cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            cameraDevice.createCaptureSession(Collections.singletonList(surface),new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    session=s;
                    try {
                        session.setRepeatingRequest(builder.build(),null,handler);
                        callback.onCameraState(true,"AR kamera açık");
                    } catch (Exception e){ callback.onCameraState(false,"Kamera önizleme hatası"); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s){ callback.onCameraState(false,"Kamera yapılandırılamadı"); }
            },handler);
        } catch (Exception e){ callback.onCameraState(false,"Kamera önizleme açılamadı"); }
    }

    public void stop() {
        requested=false;
        try { if (session!=null) session.close(); } catch (Exception ignored) {}
        session=null;
        try { if (cameraDevice!=null) cameraDevice.close(); } catch (Exception ignored) {}
        cameraDevice=null;
        callback.onCameraState(false,"Gökyüzü modu");
    }
}
