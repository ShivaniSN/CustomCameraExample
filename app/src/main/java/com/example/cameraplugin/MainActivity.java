package com.example.cameraplugin;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ZoomControls;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PictureCallback {

    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private SurfaceView surfaceView;
    private ZoomControls zoomControls;
    private ConstraintLayout constraintLayout;

    public static final int REQUEST_CODE = 100;
    private ExifInterface exifObject;
    private Bitmap bitmap;
    private int rotation;
    private String[] neededPermissions = new String[]{CAMERA, WRITE_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surface_camera);
        constraintLayout = findViewById(R.id.mainLayout);
        if (surfaceView != null) {
            boolean result = checkPermission();
            if (result) {
                setupSurfaceHolder();
            }
        }


        Date c = Calendar.getInstance().getTime();
        DateFormat date = new SimpleDateFormat("z", Locale.getDefault());
        String localTime = date.format(Calendar.getInstance().getTime());
        System.out.println("Current time => " + c);

        SimpleDateFormat df = new SimpleDateFormat("ddd MMM yyyy hh:mm:ss");
        String formattedDate = df.format(c);

        Toast.makeText(this, "Date : " + formattedDate + "TimeZone : " + localTime, Toast.LENGTH_LONG).show();
    }

    private boolean checkPermission() {
        int currentAPIVersion = Build.VERSION.SDK_INT;
        if (currentAPIVersion >= android.os.Build.VERSION_CODES.M) {
            ArrayList<String> permissionsNotGranted = new ArrayList<>();
            for (String permission : neededPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNotGranted.add(permission);
                }
            }
            if (permissionsNotGranted.size() > 0) {
                boolean shouldShowAlert = false;
                for (String permission : permissionsNotGranted) {
                    shouldShowAlert = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                }
                if (shouldShowAlert) {
                    showPermissionAlert(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                } else {
                    requestPermissions(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                }
                return false;
            }
        }
        return true;
    }

    private void showPermissionAlert(final String[] permissions) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(R.string.permission_required);
        alertBuilder.setMessage(R.string.permission_message);
        alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                requestPermissions(permissions);
            }
        });
        AlertDialog alert = alertBuilder.create();
        alert.show();
    }

    private void requestPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE:
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(MainActivity.this, R.string.permission_warning, Toast.LENGTH_LONG).show();
                        //setViewVisibility(R.id.showPermissionMsg, View.VISIBLE);
                        return;
                    }
                }

                setupSurfaceHolder();
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private void setupSurfaceHolder() {
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setBtnClick();
    }

    private void setBtnClick() {
        ImageButton startBtn = findViewById(R.id.startBtn);
        if (startBtn != null) {
            startBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    captureImage();
                }
            });
        }
    }

    public void captureImage() {
        if (camera != null) {
            camera.takePicture(null, null, this);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        startCamera();
    }

    private void startCamera() {
        enableZoom();
        camera = Camera.open();
        setCameraDisplayOrientation(this, 0, camera);
        //camera.setDisplayOrientation(90);
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        resetCamera();
    }

    public void resetCamera() {
        if (surfaceHolder.getSurface() == null) {
            // Return if preview surface does not exist
            return;
        }

        if (camera != null) {
            // Stop if preview surface is already running.
            camera.stopPreview();
            try {
                // Set preview display
                camera.setPreviewDisplay(surfaceHolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Start the camera preview...
            camera.startPreview();
        }
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        saveImage(bytes);
        resetCamera();
    }

    private void saveImage(byte[] bytes) {
        Uri uriTarget = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
        String filePath = getRealPathFromUri(this, uriTarget);
        OutputStream imageFileOS;
        Bitmap imageRotate = rotateBitmap(filePath, bytes);

        try {

            imageFileOS = getContentResolver().openOutputStream(uriTarget);
            imageRotate.compress(Bitmap.CompressFormat.PNG, 85, imageFileOS);
            //imageFileOS.write(bytes);
            imageFileOS.flush();
            imageFileOS.close();

            Toast.makeText(getApplicationContext(), "Image saved: " + uriTarget.toString(), Toast.LENGTH_LONG).show();

        } catch (FileNotFoundException e) {
            e.printStackTrace();

        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    public Bitmap rotateBitmap(String uriTargetPath, byte[] bytes) {
        Matrix matrix = new Matrix();
        Bitmap bmRotated = null;
        try {
            int rotation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            ByteArrayInputStream arrayInputStream = new ByteArrayInputStream(bytes);
            Bitmap bitmap = BitmapFactory.decodeStream(arrayInputStream);

            ExifInterface exif = new ExifInterface(uriTargetPath);
            /*int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);*/

            switch (rotation) {
                case Surface.ROTATION_0:
                    matrix.postRotate(90);
                    break;
                case Surface.ROTATION_270:
                    //nothing to do
                    matrix.postRotate(180);
                    break;
                case Surface.ROTATION_180:
                    matrix.postRotate(270);
                    break;
                case Surface.ROTATION_90:
                    matrix.postRotate(0);
                    break;
            }

            /*if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("6")) {
                matrix.postRotate(90);
            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("8")) {
                matrix.postRotate(270);
            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("3")) {
                matrix.postRotate(180);
            } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("0")) {
                matrix.postRotate(90);
            }*/

//            String attrLATITUDE = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
//            String attrLATITUDE_REF = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
//            String attrLONGITUDE = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
//            String attrLONGITUDE_REF = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);

//            Toast.makeText(getApplicationContext(), "Lat : " + attrLATITUDE + "\n LAtRef : "
//                    +attrLATITUDE_REF + "\n Long : " +attrLONGITUDE + "\n LongRef : " +attrLONGITUDE_REF, Toast.LENGTH_LONG).show();

            bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }/* catch (IOException e) {
            e.printStackTrace();
        }*/ catch (IOException e) {
            e.printStackTrace();
        }
        return bmRotated;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Toast.makeText(this, "" + newConfig.orientation, Toast.LENGTH_SHORT).show();
    }

    public static void setCameraDisplayOrientation(Activity activity, int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        //int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        // do something for phones running an SDK before lollipop
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        camera.setDisplayOrientation(result);
    }

    public static String getRealPathFromUri(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void enableZoom() {
        zoomControls = new ZoomControls(this);
        zoomControls.setIsZoomInEnabled(true);
        zoomControls.setIsZoomOutEnabled(true);
        zoomControls.setOnZoomInClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                zoomCamera(true);

            }
        });
        zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                zoomCamera(false);
            }
        });
        constraintLayout.addView(zoomControls);
    }

    public void zoomCamera(boolean zoomInOrOut) {
        if (camera != null) {
            Camera.Parameters parameter = camera.getParameters();

            if (parameter.isZoomSupported()) {
                int MAX_ZOOM = parameter.getMaxZoom();
                int currnetZoom = parameter.getZoom();
                if (zoomInOrOut && (currnetZoom < MAX_ZOOM && currnetZoom >= 0)) {
                    parameter.setZoom(++currnetZoom);
                } else if (!zoomInOrOut && (currnetZoom <= MAX_ZOOM && currnetZoom > 0)) {
                    parameter.setZoom(--currnetZoom);
                }
            } else
                Toast.makeText(this, "Zoom Not Avaliable", Toast.LENGTH_LONG).show();

            camera.setParameters(parameter);
        }
    }
}

