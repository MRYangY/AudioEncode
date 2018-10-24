package com.example.root.audiorecorderdemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private AudioRecord mAudioRecorder;
    private AudioTrack mAudioTrack;
    private int sampleRateInHz = 44100;
    private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
//    private String MINE_TYPE = "audio/mp4a-latm";
    private String MINE_TYPE = "audio/flac";

    private MediaCodec mAudioEncodeCodec;


    private Button mStart;
    private Button mStop;
    private Button mPlayer;
    private Button mEncode;
    private Button mEncodeStop;

    private byte[] mAudioDate;
    private byte buffer[] = new byte[16 * 10000];
    private byte encodeBuffer[];

    private File mAudioFile;
    private File mAACFile;
    private FileOutputStream fos;
    private FileOutputStream aacFos;
    //    private FileInputStream fis;
    private FileInputStream pcmFis;
    private volatile boolean isRecording;

    private Thread thread;
    private AudioEncodeThread audioEncodeThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStart = findViewById(R.id.bt_start);
        mStop = findViewById(R.id.bt_stop);
        mStart.setOnClickListener(mStartListener);
        mStop.setOnClickListener(mStopListener);
//        mPlayer = findViewById(R.id.bt_play);
//        mPlayer.setOnClickListener(mPlayListener);
        mEncode = findViewById(R.id.bt_encode);
        mEncode.setOnClickListener(mEncodeListener);
        mEncodeStop = findViewById(R.id.bt_encode_stop);
        mEncodeStop.setOnClickListener(mEncodeStopListener);


        //AudioRecorder 部分
        String path = Environment.getExternalStorageDirectory() + "/audioRecorder.pcm";
        mAudioFile = new File(path);
        try {
            fos = new FileOutputStream(mAudioFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        Log.e(TAG, "onCreate:minBufferSize =  " + minBufferSize);
        mAudioDate = new byte[minBufferSize * 2];
        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz
                , channelConfig, AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 2);

//        //AudioTrack 部分
//        int playMinSize = AudioTrack.getMinBufferSize(0xac44, AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT);
//        Log.e(TAG, "onCreate: playMinSize = " + playMinSize);
//        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 0xac44, AudioFormat.CHANNEL_CONFIGURATION_STEREO
//                , AudioFormat.ENCODING_PCM_16BIT, playMinSize * 2, AudioTrack.MODE_STREAM);
//        mAudioTrack.setVolume(1.0f);
//
//        try {
//            fis = new FileInputStream(mAudioFile);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }

        //MediaCodeC 编码

        String aacFilePath = Environment.getExternalStorageDirectory() + "/audioEncodeFile.flac";
        mAACFile = new File(aacFilePath);
        try {
            aacFos = new FileOutputStream(mAACFile);
            pcmFis = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        int codecCount = MediaCodecList.getCodecCount();
        for (int i = 0; i < codecCount; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            String name = info.getName();
            Log.e(TAG, "onCreate: name = " + name + "--");
            String[] supportedTypes = info.getSupportedTypes();
            Log.e(TAG, "onCreate: length = " + supportedTypes.length);
            for (String type : supportedTypes
                    ) {
                Log.e(TAG, "onCreate: supportedType[] = " + type);
            }
        }

        try {
            mAudioEncodeCodec = MediaCodec.createEncoderByType(MINE_TYPE);
            MediaFormat format = MediaFormat.createAudioFormat(MINE_TYPE, sampleRateInHz, 2);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 256000);
//            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mAudioEncodeCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private View.OnClickListener mStartListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            isRecording = true;
            thread = new MyThread("aaaa");
            thread.start();
        }
    };

    private View.OnClickListener mStopListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.e(TAG, "mStopListener  onClick: ");
            isRecording = false;
            thread = null;
            mAudioRecorder.stop();
            mAudioRecorder.release();
            mAudioRecorder = null;
        }
    };

//    private View.OnClickListener mPlayListener = new View.OnClickListener() {
//        @Override
//        public void onClick(View v) {
//            mAudioTrack.play();
//            try {
//                while (fis.read(buffer) > 0) {
//                    Log.e(TAG, "onClick: buffer.length = " + buffer.length);
//                    mAudioTrack.write(buffer, 0, buffer.length);
//                }
//            } catch (IOException e) {
//
//            }
//        }
//    };

    private void audioEncode() {
        try {
            ByteBuffer[] inputBuffers = mAudioEncodeCodec.getInputBuffers();
            int inputBufferIndex = mAudioEncodeCodec.dequeueInputBuffer(-1);
            Log.e(TAG, "audioEncode:inputBufferIndex = " + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    inputBuffer = mAudioEncodeCodec.getInputBuffer(inputBufferIndex);
                } else {
                    inputBuffer = inputBuffers[inputBufferIndex];
                }
                inputBuffer.clear();
                int lenght = mAudioRecorder.read(inputBuffer, mAudioDate.length);
                mAudioEncodeCodec.queueInputBuffer(inputBufferIndex, 0, lenght
                        , System.nanoTime() / 1000, 0);

            } else {
                Log.e(TAG, "  --No avaliable input buffer");
            }
        } catch (Exception e) {
            Log.e(TAG, "  --audio encoder getInputBuffers:exception " + e.getMessage());
        }


        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            ByteBuffer[] outputBuffers = mAudioEncodeCodec.getOutputBuffers();
            int outputBufferIndex = mAudioEncodeCodec.dequeueOutputBuffer(info, 0);
            Log.e(TAG, "  --audioEncode: outputBufferIndex = " + outputBufferIndex);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    outputBuffer = mAudioEncodeCodec.getOutputBuffer(outputBufferIndex);
                } else {
                    outputBuffer = outputBuffers[outputBufferIndex];
                }
                int infoSize = info.size;
//                int outputBufferSize = infoSize + 7;
//                byte[] outData = new byte[outputBufferSize];
//                if (outputBuffer != null) {
//                    outputBuffer.position(info.offset);
//                    outputBuffer.get(outData, 7, infoSize);
//                    addADTStoPacket(outData, outputBufferSize);
//                }
                byte[] outData = new byte[infoSize];
                outputBuffer.get(outData);
                aacFos.write(outData);
                mAudioEncodeCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mAudioEncodeCodec.dequeueOutputBuffer(info, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "  --audio encoder getOutputBuffers:exception " + e.getMessage());
        }
    }

    private View.OnClickListener mEncodeListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            isRecording = true;
            audioEncodeThread = new AudioEncodeThread("audio-encode-thread");
            audioEncodeThread.start();

        }
    };

    private View.OnClickListener mEncodeStopListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                isRecording = false;
                audioEncodeThread.join();
                audioEncodeThread = null;
                mAudioEncodeCodec.stop();
                mAudioEncodeCodec.release();
                mAudioEncodeCodec = null;
                mAudioRecorder.stop();
                mAudioRecorder.release();
                mAudioRecorder = null;

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    };

    /**
     * 添加ADTS头
     *
     * @param packet
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 4; // 44.1KHz
        int chanCfg = 2; // CPE

        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    class AudioEncodeThread extends Thread {
        public AudioEncodeThread(@NonNull String name) {
            super(name);
        }

        @Override
        public void run() {
            super.run();
            try {
                mAudioEncodeCodec.start();
                mAudioRecorder.startRecording();
                while (isRecording) {

                    audioEncode();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    class MyThread extends Thread {
        public MyThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            super.run();
            mAudioRecorder.startRecording();
            int length = -1;
            do {
                length = mAudioRecorder.read(mAudioDate, 0, mAudioDate.length);
                Log.e(TAG, "onClick: length =" + length);
                try {
                    Log.e(TAG, "run: " + mAudioDate.length);
                    fos.write(mAudioDate);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } while (length >= 0 && isRecording);
        }
    }
}
