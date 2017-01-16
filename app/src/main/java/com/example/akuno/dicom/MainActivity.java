package com.example.akuno.dicom;

import android.graphics.Bitmap;
import android.os.Environment;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.imebra.CodecFactory;
import com.imebra.ColorTransformsFactory;
import com.imebra.DataSet;
import com.imebra.DrawBitmap;
import com.imebra.FileStreamInput;
import com.imebra.Image;
import com.imebra.LUT;
import com.imebra.ReadingDataHandlerNumeric;
import com.imebra.StreamReader;
import com.imebra.TagId;
import com.imebra.TransformsChain;
import com.imebra.VOILUT;
import com.imebra.VOIs;
import com.imebra.drawBitmapType_t;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        System.loadLibrary("imebra_lib");

        String path = "android.resource://" + getPackageName() + "/" + R.raw.brain_001;

        FileStreamInput file = new FileStreamInput(path);

        DataSet loadedDataSet= CodecFactory.load(new StreamReader(file), 256);

        //DicomFile.dcm

        String patientName = loadedDataSet.getString(new TagId(0x10, 0x10), 0);
        String patientId = loadedDataSet.getString(new TagId(0x10, 0x20), 0);
        String patientBirth = loadedDataSet.getString(new TagId(0x10, 0x30), 0);

        TextView name = (TextView) findViewById(R.id.patientname);
        TextView id = (TextView) findViewById(R.id.patientid);
        TextView birth = (TextView) findViewById(R.id.patientbirth);

        name.setText(patientName);
        id.setText(patientId);
        birth.setText(patientBirth);


        Image image = loadedDataSet.getImageApplyModalityTransform(0);

        String colorSpace = image.getColorSpace();

        long width = image.getWidth();
        long height = image.getHeight();

        ReadingDataHandlerNumeric dataHandlerNumeric = loadedDataSet.getReadingDataHandlerNumeric(new TagId(0x10, 0x10), 0);


        for(long scanY = 0; scanY != height; scanY++)
        {
            for(long scanX = 0; scanX != width; scanX++)
            {
                // For monochrome images
                int luminance = dataHandlerNumeric.getSignedLong(scanY * width + scanX);
                // For RGB images
                int r = dataHandlerNumeric.getSignedLong((scanY * width + scanX) * 3);
                int g = dataHandlerNumeric.getSignedLong((scanY * width + scanX) * 3 + 1);
                int b = dataHandlerNumeric.getSignedLong((scanY * width + scanX) * 3 + 2);
            }
        }

        TransformsChain chain = new TransformsChain();

        if(ColorTransformsFactory.isMonochrome(image.getColorSpace())) {

            VOILUT voilutTransform = new VOILUT();

            VOIs vois = loadedDataSet.getVOIs();

            List<LUT> luts = new ArrayList<LUT>();

            for(long scanLUTs = 0; ; scanLUTs++) {
                try {
                    luts.add(loadedDataSet.getLUT(new TagId(0x0028, 0x3010), scanLUTs));
                } catch (Exception e) {
                    break;
                }
            }

            if(!vois.isEmpty()) {
                voilutTransform.setCenterWidth(vois.get(0).getCenter(), vois.get(0).getWidth());
            } else if (!luts.isEmpty()){
                voilutTransform.setLUT(luts.get(0));
            } else {
                voilutTransform.applyOptimalVOI(image, 0, 0, width, height);
            }
            chain.addTransform(voilutTransform);
        }

        DrawBitmap draw = new DrawBitmap(chain);

        long requestedBufferSize = draw.getBitmap(image, drawBitmapType_t.drawBitmapRGBA, 4, new byte[0]);

        byte buffer[] = new byte [(int) requestedBufferSize];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        draw.getBitmap(image, drawBitmapType_t.drawBitmapRGBA, 4 , buffer);
        Bitmap renderBitmap = Bitmap.createBitmap((int) image.getWidth(), (int) image.getHeight(), Bitmap.Config.ARGB_8888);
        renderBitmap.copyPixelsFromBuffer(byteBuffer);


    }
}
