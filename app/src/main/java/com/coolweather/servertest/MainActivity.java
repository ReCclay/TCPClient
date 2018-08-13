package com.coolweather.servertest;

import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Timer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    Button buttonConnect;
    EditText editTextIP;
    EditText editTextPort;
    TextView textViewShowData;

    String stringIP = "";//用户输入的IP地址
    InetAddress ipAddress;//IP对应的主机地址(不必赋初值)
    int port = 0;//端口号

    //连接服务器
    Socket socket = null;//定义socket
    Thread mthreadConnectServer;//记录连接服务器任务，销毁Thread用！
    boolean connectServerFlag = true;//连接标志 1-表示接下来要进行连接，此时状态是断开
    //读取数据
    InputStream inputStream = null;//获取输入流
    Thread mthreadReadData;//记录读取数据任务，销毁Thread用！
    boolean threadReadDataFlag = false;//线程读取标志
    byte[] readDataBuff = new byte[1024];//存储接收到的数据
    int readDataBuffLength = 0;//读取数据长度
    //发送数据
    OutputStream outputStream = null;//获得输出流
    Thread mthreadSendData;//记录发送数据任务，销毁Thread用!
    boolean threadSendDataFlag = false;//线程发送标志
    byte[] sendDataBuff = new byte[1024];//存储要发送的数据
    int sendDataBuffLength = 0;//控制发送数据的个数

    private SharedPreferences sharedPreferences;//存储数据
    private SharedPreferences.Editor editor;//存储数据

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonConnect = (Button) findViewById(R.id.button);
        editTextIP = (EditText) findViewById(R.id.editText11);
        editTextPort = (EditText) findViewById(R.id.editText12);
        textViewShowData = (TextView) findViewById(R.id.textView13);

        buttonConnect.setOnClickListener(buttonConnectClickListener);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean issave = sharedPreferences.getBoolean("SAVE", false);//得到save文件存的值，得不到会返回false
        if (issave)
        {
            String string_ip = sharedPreferences.getString("IP", "192.168.4.1");//取出ip,不存在返回192.168.4.1
            String int_port = sharedPreferences.getString("PORT", "8080");//取出端口号,不存在返回8080
            editTextIP.setText(string_ip);
            editTextPort.setText(int_port);
        }
    }

    /*
    点击按钮连接(断开)服务器
    */
    private View.OnClickListener buttonConnectClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (connectServerFlag) {//未执行前状态是断开
                try {
                    stringIP = editTextIP.getText().toString().replace(" ", "");//从editTextIP控件获取输入的ip地址，并滤除空格
                    port = Integer.valueOf(editTextPort.getText().toString().replace(" ", ""));//editTextPort控件获取端口号，并滤除其中的空格

                    ThreadConnectServer threadConnectServer = new ThreadConnectServer();//创建连接任务
                    threadConnectServer.start();//启动连接任务
                    mthreadConnectServer = threadConnectServer;//记录连接任务
                    Timer.start();

                    editor = sharedPreferences.edit();
                    editor.putString("IP", stringIP);//记录ip
                    editor.putString("PORT", editTextPort.getText().toString());//记录端口号
                    editor.putBoolean("SAVE", true);//写入记录标志
                    editor.commit();
                }
                catch (Exception e){

                }
            }
            else {//未执行前状态是连接
                Timer.cancel();
                threadReadDataFlag = false;//退出读取数据状态
                threadSendDataFlag = false;//退出发送数据状态
                connectServerFlag = true;//进入断开状态

                try {mthreadReadData.interrupt();} catch (Exception e1) {}//结束接收数据线程 //注意这里需要改下Exception的变量
                try {mthreadSendData.interrupt();} catch (Exception e1) {}//结束发送数据线程 //注意这里需要改下Exception的变量
                try {mthreadConnectServer.interrupt();} catch (Exception e1) {}//结束连接服务器线程

                try {socket.close();} catch (Exception e1){} //关闭socket//有了这两句会跳到log333那里？？？
                try {inputStream.close();} catch (Exception e1){}//关闭数据流
                try {outputStream.close();} catch (Exception e1){}
            }
        }
    };

    /*
    连接服务器线程
     */
    class ThreadConnectServer extends Thread
    {
        public void run()
        {
            try {
                ipAddress = InetAddress.getByName(stringIP);//获取IP地址
                socket = new Socket(ipAddress, port);//创建连接地址和端口//注意只有连接上这个线程才会往下走，否则将一直阻塞在这里！

                connectServerFlag = false;//这是才能说明是连接上，然后置标志位！

                //socket建立好后，开启输入输出流，进行数据处理
                inputStream = socket.getInputStream();//获取通道的输入流
                outputStream = socket.getOutputStream();//获取通道的输出流
                threadReadDataFlag = true;//进入接收数据状态
                threadSendDataFlag = true;//进入发送数据状态
                readDataBuffLength = 0;//初始化接收数据的个数值
                sendDataBuffLength = 0;//初始化发送数据的个数值

                try{//创建线程开始接收数据(接收数据是一个比较耗时的操作)
                    ThreadReadData threadReadData = new ThreadReadData();
                    threadReadData.start();
                    mthreadReadData = threadReadData;
                }
                catch(Exception e){

                }
                try{//创建线程开始发送数据
                    ThreadSendData threadSendData = new ThreadSendData();
                    threadSendData.start();
                    mthreadSendData = threadSendData;
                }
                catch (Exception e){

                }

                runOnUiThread(new Runnable() {
                    public void run() {//更新UI
                        buttonConnect.setText("断开");
                        Toast.makeText(getApplicationContext(), "连接成功", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /*
    读取数据线程
     */
    class ThreadReadData extends Thread
    {
        @Override
        public void run() {
            while(threadReadDataFlag){//注意，这里的while。
                try{//读取数据
                    readDataBuffLength = inputStream.read(readDataBuff);//读取服务器返回数据

                    if(readDataBuffLength == -1) {//服务器断开会返回-1
                        Timer.cancel();
                        threadReadDataFlag = false;//退出读取数据状态
                        threadSendDataFlag = false;//退出发送数据状态
                        connectServerFlag = true;//进入断开状态

                        try {mthreadReadData.interrupt();} catch (Exception e1) {}//结束接收数据线程 //注意这里需要改下Exception的变量
                        try {mthreadSendData.interrupt();} catch (Exception e1) {}//结束发送数据线程 //注意这里需要改下Exception的变量
                        try {mthreadConnectServer.interrupt();} catch (Exception e1) {}//结束连接服务器线程

                        runOnUiThread(new Runnable() {
                            public void run() {//更新UI
                                buttonConnect.setText("连接");
                                Toast.makeText(getApplicationContext(), "与服务器断开连接", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "run: 222" );
                            }
                        });
                    }
                    else//读取到服务器返回的正确的数据
                    {
                        //进行相应的数据处理
                        if((readDataBuff[0]&0xFF) == 0x55)//注意byte转int要记得&0xFF
                        {
                            runOnUiThread(new Runnable() {
                                public void run() {//更新UI
                                    textViewShowData.setText("温度："+(readDataBuff[1]&0xFF)+"\n"+"\n"+"湿度："+(readDataBuff[2]&0xFF));//注意byte转int要记得&0xFF
                                }
                            });
                        }

                    }
                }
                catch (Exception e){
                    Timer.cancel();
                    threadReadDataFlag = false;//退出读取数据状态
                    threadSendDataFlag = false;//退出发送数据状态
                    connectServerFlag = true;//进入断开状态

                    try {mthreadReadData.interrupt();} catch (Exception e1) {}//结束接收数据线程 //注意这里需要改下Exception的变量
                    try {mthreadSendData.interrupt();} catch (Exception e1) {}//结束发送数据线程 //注意这里需要改下Exception的变量
                    try {mthreadConnectServer.interrupt();} catch (Exception e1) {}//结束连接服务器线程

                    runOnUiThread(new Runnable() {
                        public void run() {//更新UI
                            buttonConnect.setText("连接");
                            Toast.makeText(getApplicationContext(), "与服务器断开连接", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "run: 333" );
                        }
                    });
                }
            }
        }
    }

    /*
    发送数据线程
     */
    class ThreadSendData extends Thread
    {
        @Override
        public void run() {
            while(threadSendDataFlag){
                if(sendDataBuffLength > 0)
                {
                    try{
                        outputStream.write(sendDataBuff, 0, sendDataBuffLength);
                        sendDataBuffLength = 0;
                    }
                    catch (Exception e){
                        Timer.cancel();
                        threadReadDataFlag = false;//退出读取数据状态
                        threadSendDataFlag = false;//退出发送数据状态
                        connectServerFlag = true;//进入断开状态

                        try {mthreadReadData.interrupt();} catch (Exception e1) {}//结束接收数据线程 //注意这里需要改下Exception的变量
                        try {mthreadSendData.interrupt();} catch (Exception e1) {}//结束发送数据线程 //注意这里需要改下Exception的变量
                        try {mthreadConnectServer.interrupt();} catch (Exception e1) {}//结束连接服务器线程

                        runOnUiThread(new Runnable() {
                            public void run() {//更新UI
                                buttonConnect.setText("连接");
                                Toast.makeText(getApplicationContext(), "与服务器断开连接", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "run: 444" );
                            }
                        });
                    }
                }
            }

        }
    }

    /*
    每隔100ms进入onTick，1000ms后进入onFinsh
    */
    private CountDownTimer Timer = new CountDownTimer(1000,100) {
        @Override
        public void onTick(long millisUntilFinished) {

        }

        @Override
        public void onFinish()
        {
            //相应的操作
            sendDataBuff[0] = 0x55;
            sendDataBuff[1] = 0x55;
            sendDataBuff[2] = 0x01;
            sendDataBuffLength = 3;//控制发送数据的个数
            Timer.start();
        }
    };

    /*
    onStop在活动完全被遮盖的时候调用
     */
    @Override
    protected void onStop() {
        Timer.cancel();
        threadReadDataFlag = false;//退出读取数据状态
        threadSendDataFlag = false;//退出发送数据状态
        super.onStop();
    }
}
