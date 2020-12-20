package com.visualsearch.app;




import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Size;

import com.visualsearch.app.env.ImageUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;


@RunWith(AndroidJUnit4.class)
public class DetectorTest {

    private static final int MODEL_INPUT_SIZE = 300;
    private static final boolean IS_MODEL_QUANTIZED = true;
    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABELS_FILE = "labelmap.txt";
    private static final Size IMAGE_SIZE = new Size(640, 480);

    private Detector detector;
    private Bitmap croppedBitmap;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    @Before
    public void setUp() throws IOException {
        detector =
                TFLiteObjectDetectionAPIModel.create(
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        MODEL_FILE,
                        LABELS_FILE,
                        MODEL_INPUT_SIZE,
                        IS_MODEL_QUANTIZED);
        int cropSize = MODEL_INPUT_SIZE;
        int previewWidth = IMAGE_SIZE.getWidth();
        int previewHeight = IMAGE_SIZE.getHeight();
        int sensorOrientation = 0;
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, false);
        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
    }

    @Test
    public void detectionResultsShouldNotChange() throws Exception {
        Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(loadImage("table.jpg"), frameToCropTransform, null);
        final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
        final List<Detector.Recognition> expected = loadRecognitions("table_results.txt");

        for (Detector.Recognition target : expected) {
            // Find a matching result in results
            boolean matched = false;
            for (Detector.Recognition item : results) {
                RectF bbox = new RectF();
                cropToFrameTransform.mapRect(bbox, item.getLocation());
                if (item.getTitle().equals(target.getTitle())
                        && matchBoundingBoxes(bbox, target.getLocation())
                        && matchConfidence(item.getConfidence(), target.getConfidence())) {
                    matched = true;
                    break;
                }
            }
            assertThat(matched).isTrue();
        }
    }

    // Confidence tolerance: absolute 1%
    private static boolean matchConfidence(float a, float b) {
        return abs(a - b) < 0.01;
    }

    // Bounding Box tolerance: overlapped area > 95% of each one
    private static boolean matchBoundingBoxes(RectF a, RectF b) {
        float areaA = a.width() * a.height();
        float areaB = b.width() * b.height();

        RectF overlapped =
                new RectF(
                        max(a.left, b.left), max(a.top, b.top), min(a.right, b.right), min(a.bottom, b.bottom));
        float overlappedArea = overlapped.width() * overlapped.height();
        return overlappedArea > 0.95 * areaA && overlappedArea > 0.95 * areaB;
    }

    private static Bitmap loadImage(String fileName) throws Exception {
        AssetManager assetManager =
                InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        InputStream inputStream = assetManager.open(fileName);
        return BitmapFactory.decodeStream(inputStream);
    }


    private static List<Detector.Recognition> loadRecognitions(String fileName) throws Exception {
        AssetManager assetManager =
                InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        InputStream inputStream = assetManager.open(fileName);
        Scanner scanner = new Scanner(inputStream);
        List<Detector.Recognition> result = new ArrayList<>();
        while (scanner.hasNext()) {
            String category = scanner.next();
            category = category.replace('_', ' ');
            if (!scanner.hasNextFloat()) {
                break;
            }
            float left = scanner.nextFloat();
            float top = scanner.nextFloat();
            float right = scanner.nextFloat();
            float bottom = scanner.nextFloat();
            RectF boundingBox = new RectF(left, top, right, bottom);
            float confidence = scanner.nextFloat();
            Detector.Recognition recognition = new Detector.Recognition(null, category, confidence, boundingBox);
            result.add(recognition);
        }
        return result;
    }
}
