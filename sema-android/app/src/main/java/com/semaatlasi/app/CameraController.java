package com.semaatlasi.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.view.TextureView;

import java.util.Collections;

public class CameraController {
    public interface Callback {
        void onCameraState(boolean on, String message);
        default void onCameraFov(double horizontalDeg, double verticalDeg) {}
    }

    private final Activity activity;
    private final TextureView textureView;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private String cameraId;
    private Size previewSize;
    private boolean requested;
    private int sensorOrientation = 90;
    private double sensorHFov = 62;
    private double sensorVFov = 48;

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
            if (cameraId==null) {
                requested=false;
                callback.onCameraState(false,"Arka kamera bulunamadı");
                return;
            }
            if (textureView.isAvailable()) {
                configureTransform(textureView.getWidth(),textureView.getHeight());
                open(cm);
            } else {
                textureView.setSurfaceTextureListener(surfaceListener);
            }
        } catch (Exception e) {
            requested=false;
            callback.onCameraState(false,"Kamera açılamadı");
        }
    }

    private void chooseCamera(CameraManager cm) throws Exception {
        for (String id:cm.getCameraIdList()) {
            CameraCharacteristics ch=cm.getCameraCharacteristics(id);
            Integer facing=ch.get(CameraCharacteristics.LENS_FACING);
            if (facing==null || facing!=CameraCharacteristics.LENS_FACING_BACK) continue;

            cameraId=id;
            Integer so=ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (so!=null) sensorOrientation=so;

            SizeF physical=ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float[] focals=ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (physical!=null && focals!=null && focals.length>0 && focals[0]>0) {
                sensorHFov=Math.toDegrees(2.0*Math.atan(physical.getWidth()/(2.0*focals[0])));
                sensorVFov=Math.toDegrees(2.0*Math.atan(physical.getHeight()/(2.0*focals[0])));
            }

            android.hardware.camera2.params.StreamConfigurationMap map=
                    ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map!=null) {
                Size[] sizes=map.getOutputSizes(SurfaceTexture.class);
                if (sizes!=null && sizes.length>0) {
                    previewSize=choosePreviewSize(sizes);
                }
            }
            return;
        }
    }

    private Size choosePreviewSize(Size[] sizes) {
        int vw=Math.max(1,textureView.getWidth());
        int vh=Math.max(1,textureView.getHeight());
        double targetLandscapeAspect=(double)vh/vw;
        Size best=sizes[0];
        double bestScore=Double.MAX_VALUE;
        for (Size s:sizes) {
            if (s.getWidth()>2560 || s.getHeight()>1440) continue;
            double aspect=(double)s.getWidth()/s.getHeight();
            double aspectPenalty=Math.abs(Math.log(aspect/targetLandscapeAspect))*3.0;
            double sizePenalty=Math.abs((s.getWidth()*s.getHeight())-(1920.0*1080.0))/(1920.0*1080.0);
            double score=aspectPenalty+sizePenalty*.15;
            if (score<bestScore) { best=s; bestScore=score; }
        }
        return best;
    }

    private final TextureView.SurfaceTextureListener surfaceListener=new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height) {
            try {
                configureTransform(width,height);
                open((CameraManager)activity.getSystemService(Context.CAMERA_SERVICE));
            } catch (Exception ignored) {}
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height) {
            configureTransform(width,height);
        }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){ return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private void configureTransform(int viewWidth,int viewHeight) {
        if (previewSize==null || viewWidth<=0 || viewHeight<=0) return;

        Matrix matrix=new Matrix();
        float cx=viewWidth/2f, cy=viewHeight/2f;

        // Camera stream is landscape; app is portrait. Rotate around optical center,
        // then center-crop without stretching the image.
        RectF viewRect=new RectF(0,0,viewWidth,viewHeight);
        RectF bufferRect;
        boolean quarterTurn=(sensorOrientation==90 || sensorOrientation==270);
        if (quarterTurn) bufferRect=new RectF(0,0,previewSize.getHeight(),previewSize.getWidth());
        else bufferRect=new RectF(0,0,previewSize.getWidth(),previewSize.getHeight());

        bufferRect.offset(cx-bufferRect.centerX(),cy-bufferRect.centerY());
        matrix.setRectToRect(viewRect,bufferRect,Matrix.ScaleToFit.FILL);
        float scale=Math.max(
                (float)viewHeight/bufferRect.height(),
                (float)viewWidth/bufferRect.width());
        matrix.postScale(scale,scale,cx,cy);
        matrix.postRotate(sensorOrientation,cx,cy);
        textureView.setTransform(matrix);

        updateEffectiveFov(viewWidth,viewHeight);
    }

    private void updateEffectiveFov(int viewWidth,int viewHeight) {
        if (previewSize==null || viewHeight<=0) return;

        boolean quarterTurn=(sensorOrientation==90 || sensorOrientation==270);
        double rawHFov=quarterTurn?sensorVFov:sensorHFov;
        double rawVFov=quarterTurn?sensorHFov:sensorVFov;
        double imageAspect=quarterTurn
                ? (double)previewSize.getHeight()/previewSize.getWidth()
                : (double)previewSize.getWidth()/previewSize.getHeight();
        double viewAspect=(double)viewWidth/viewHeight;

        double effectiveH=rawHFov;
        double effectiveV=rawVFov;
        if (viewAspect<imageAspect) {
            effectiveH=Math.toDegrees(2*Math.atan(
                    Math.tan(Math.toRadians(rawHFov/2.0))*(viewAspect/imageAspect)));
        } else if (viewAspect>imageAspect) {
            effectiveV=Math.toDegrees(2*Math.atan(
                    Math.tan(Math.toRadians(rawVFov/2.0))*(imageAspect/viewAspect)));
        }
        callback.onCameraFov(effectiveH,effectiveV);
    }

    private void open(CameraManager cm) throws Exception {
        if (!requested || cameraDevice!=null) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) return;
        cm.openCamera(cameraId,new CameraDevice.StateCallback() {
            @Override public void onOpened(CameraDevice camera) {
                cameraDevice=camera;
                requested=false;
                createPreview();
            }
            @Override public void onDisconnected(CameraDevice camera){
                camera.close(); cameraDevice=null; requested=false;
                callback.onCameraState(false,"Kamera bağlantısı kesildi");
            }
            @Override public void onError(CameraDevice camera,int error){
                camera.close(); cameraDevice=null; requested=false;
                callback.onCameraState(false,"Kamera hatası");
            }
        },handler);
    }

    private void createPreview() {
        try {
            SurfaceTexture st=textureView.getSurfaceTexture();
            if (st==null||cameraDevice==null) return;
            if (previewSize!=null) st.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());
            configureTransform(textureView.getWidth(),textureView.getHeight());

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
                    } catch (Exception e){
                        callback.onCameraState(false,"Kamera önizleme hatası");
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s){
                    callback.onCameraState(false,"Kamera yapılandırılamadı");
                }
            },handler);
        } catch (Exception e){
            callback.onCameraState(false,"Kamera önizleme açılamadı");
        }
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
