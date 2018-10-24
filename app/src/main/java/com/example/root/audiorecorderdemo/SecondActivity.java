package com.example.root.audiorecorderdemo;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;

import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Create by rain
 * <p>
 * DATE:18-10-24
 * <p>
 * Describe:
 **/
public class SecondActivity extends Activity {

    private static final String TAG = "SecondActivity";

    private final String MINE_TYPE = "audio/mp4a-latm";
    private int rate = 256000;
    //录音设置
    private int sampleRate = 44100;   //采样率，默认44.1k
    private int channelCount = 2;     //音频采样通道，默认2通道
    private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;        //通道设置，默认立体声
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;     //设置采样数据格式，默认16比特PCM
    private byte[] buffer;
    private int bufferSize;
    private volatile boolean isRecording;

    private Button mStart;
    private Button mStop;

    private String fileStore;
    private FileOutputStream fos;

    private MediaCodec mEncode;
    private AudioRecord mRecord;

    private MyThread mThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_second);
        mStart = findViewById(R.id.start_recorder);
        mStop = findViewById(R.id.stop_recorder);
        mStart.setOnClickListener(mStartListener);
        mStop.setOnClickListener(mStopListener);

        fileStore = Environment.getExternalStorageDirectory() + "/rain.aac";
        try {
            fos = new FileOutputStream(fileStore);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        MediaFormat format = MediaFormat.createAudioFormat(MINE_TYPE, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, rate);

        try {
            mEncode = MediaCodec.createEncoderByType(MINE_TYPE);
            mEncode.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2;
        buffer = new byte[bufferSize];
        mRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                audioFormat, bufferSize);
    }

    private View.OnClickListener mStartListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mEncode.start();
            mRecord.startRecording();
            isRecording = true;
            if (mThread == null) {
                mThread = new MyThread("yin pin bian ma");
                mThread.start();
            }
        }
    };

    private View.OnClickListener mStopListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            stop();
        }
    };


    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }


    class MyThread extends Thread {
        public MyThread(@NonNull String name) {
            super(name);
        }

        @Override
        public void run() {
            super.run();
            while (isRecording) {
                try {
                    readOutputData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    //TODO Add End Flag
    private void readOutputData() throws IOException {
        int index = mEncode.dequeueInputBuffer(-1);
        Log.e(TAG, "readOutputData: index=" + index);
        if (index >= 0) {
            final ByteBuffer buffer = getInputBuffer(index);
            buffer.clear();
            int length = mRecord.read(buffer, bufferSize);
            Log.e(TAG, "length-->" + length);
            if (length > 0) {
                mEncode.queueInputBuffer(index, 0, length, System.nanoTime() / 1000, 0);
            } else {
            }
        }
        MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
        int outIndex;
        do {
            outIndex = mEncode.dequeueOutputBuffer(mInfo, 0);
            Log.e(TAG, "audio flag---->" + mInfo.flags + "/" + outIndex);
            if (outIndex >= 0) {
                ByteBuffer buffer = getOutputBuffer(outIndex);
                buffer.position(mInfo.offset);
                byte[] temp = new byte[mInfo.size + 7];
                buffer.get(temp, 7, mInfo.size);
                addADTStoPacket(temp, temp.length);
                fos.write(temp);
                mEncode.releaseOutputBuffer(outIndex, false);
            } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.e(TAG, "readOutputData: INFO_TRY_AGAIN_LATER");

            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.e(TAG, "readOutputData: INFO_OUTPUT_FORMAT_CHANGED");
            }
        } while (outIndex >= 0);
    }

    private ByteBuffer getInputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mEncode.getInputBuffer(index);
        } else {
            return mEncode.getInputBuffers()[index];
        }
    }

    private ByteBuffer getOutputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mEncode.getOutputBuffer(index);
        } else {
            return mEncode.getOutputBuffers()[index];
        }
    }


    /**
     * 停止录制
     */
    public void stop() {
        try {
            isRecording = false;
            mThread.join();
            mThread = null;
            mRecord.stop();
            mEncode.stop();
            mEncode.release();
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
