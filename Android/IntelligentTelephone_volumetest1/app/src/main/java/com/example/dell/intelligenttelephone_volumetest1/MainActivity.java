package com.example.dell.intelligenttelephone_volumetest1;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;

import static java.lang.StrictMath.addExact;
import static java.lang.StrictMath.max;
import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity {

    private ImageView setBell,setCall;
    private TextView showBell,showCall;
    private MediaRecorder recordNoise;
    private MediaPlayer playBell,playCall;
    private String[] permissions=new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private String fileName=Environment.getExternalStorageDirectory().getAbsolutePath() + "/noise.amr";
    private int amplitude,bellVolume,callVolume;
    private double noiseVolume;
    public double fBellVolume,fCallVolume;
    private boolean isPlayingBell=false,isPlayingCall=false;
    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //隐藏标题栏
        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.hide();
        }

        setBell = findViewById(R.id.buttonBell);
        setCall = findViewById(R.id.buttonCall);
        showBell = findViewById(R.id.infoBell);
        showCall = findViewById(R.id.infoCall);

        setBell.setOnClickListener(new Click());
        setCall.setOnClickListener(new Click());

        //注册服务
        audioManager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        //动态申请权限
        int i;
        for (i = 0; i < permissions.length; i++)
            if (ContextCompat.checkSelfPermission(MainActivity.this, permissions[i]) != PackageManager.PERMISSION_GRANTED)
                break;
        if (i!=permissions.length)
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String permissions[],int[] grantResults){
        switch (requestCode){
            case 0:{
                for (int i=0;i<grantResults.length;i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                        Toast.makeText(this, permissions[i] + "授权成功！", Toast.LENGTH_SHORT).show();
                    else {
                        Toast.makeText(this, permissions[i]+"授权失败.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    protected class Click implements View.OnClickListener{
        @Override
        public void onClick(View v){

            if (v==setBell && !isPlayingBell) {
                //获取周围环境噪声值
                getNoise();

                //通过扬声器（默认）播放示例铃声
                isPlayingBell=true;
                playBell = MediaPlayer.create(MainActivity.this, R.raw.bell);
                playBell.start();
                playBell.setLooping(true);
            }

            else if (v==setBell && isPlayingBell){
                //停止播放示例铃声
                isPlayingBell=false;
                playBell.stop();
                playBell.release();

                //获取用户设置的来电铃声大小（由于这里是通过播放媒体音乐来模拟来电响铃，所以应该获取的是STREAM_MUSIC而不是实际的STREAM_RING）
                bellVolume=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

                showBell.setText(String.format("当前环境噪声值（振幅）：%d\n当前环境音量值（分贝）：%.2f\n您设置的来电铃声音量值：%d\n",amplitude,noiseVolume,bellVolume));
            }

            else if (v==setCall && !isPlayingCall){
                //获取周围环境噪声值
                getNoise();

                //通过听筒播放示例语音
                audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
                isPlayingCall=true;
                playCall=MediaPlayer.create(MainActivity.this,R.raw.call);
                playCall.start();
            }

            else if (v==setCall && isPlayingCall){
                //停止播放示例语音
                isPlayingCall=false;
                playCall.stop();
                playCall.release();

                //获取用户设置的来电铃声大小（由于这里是通过播放媒体音乐来模拟来电响铃，所以应该获取的是STREAM_MUSIC而不是实际的STREAM_RING）
                callVolume=audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

                showCall.setText(String.format("当前环境噪声值（振幅）：%d\n当前环境音量值（分贝）：%.2f\n您设置的通话音量值：%d\n",amplitude,noiseVolume,callVolume));
            }

        }
    }

    public void getNoise(){
        //为避免sleep()使得整个程序休眠，应该开启一个新线程使录音单独执行
        new Thread(new Runnable() {
            @Override
            public void run() {
                recordNoise = new MediaRecorder();
                recordNoise.setAudioSource(MediaRecorder.AudioSource.MIC);
                recordNoise.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recordNoise.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recordNoise.setOutputFile(fileName);
                try {
                    recordNoise.prepare();
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
                recordNoise.start();

                //计算环境噪声音量（开启新线程，与录音线程并发执行）
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //第一次调用getMaxAmplitude()会返回0，所以要在计算前先调用一次= =
                        recordNoise.getMaxAmplitude();
                        try {
                            sleep(500);
                        } catch (InterruptedException e) {
                            System.out.println(e.toString());
                        }
                        //获取振幅并计算分贝值
                        amplitude = recordNoise.getMaxAmplitude();
                        if (amplitude > 1)
                            noiseVolume = 20 * Math.log10((double) amplitude);
                    }
                }).start();
            }
        }).start();

        //环境噪声音量测试一秒后自动停止（在自习环境下的宿舍测试表明1秒是在结果较准确（稳定）的前提下最短的测试时间）
        try {
            sleep(1000);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }
        recordNoise.stop();
        recordNoise.reset();
        recordNoise.release();

    }

}