package com.Yubai.TouchDX;

import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class MaimaiTouchClient {
    private static final String TAG = "MaimaiTouchClient";
    private SocketChannel socketChannel;
    private boolean isRunning = false;
    private long currentState = 0L;
    private Object lock = new Object();
    private String serverIp;
    private int serverPort;
    private Thread networkThread;
    public boolean isDiagnostic = false;
    private OnDiagnosticListener diagnosticListener;
    private static final Map<String, Integer> BUTTON_MAP = new HashMap<String, Integer>();
    private long lastPingTime = 0L;

    public void setDiagnosticListener(OnDiagnosticListener onDiagnosticListener) {
        this.diagnosticListener = onDiagnosticListener;
    }

    public void connect(String string, int n) {
        this.serverIp = string;
        this.serverPort = n;
        this.isRunning = true;
        this.networkThread = new Thread(new Runnable(){

            @Override
            public void run() {
                MaimaiTouchClient.this.networkLoop();
            }
        });
        this.networkThread.start();
    }

    private void networkLoop() {
        while (this.isRunning) {
            try {
                Log.d(TAG, "Connecting to " + this.serverIp + ":" + this.serverPort);
                this.socketChannel = SocketChannel.open();
                this.socketChannel.configureBlocking(true); // initially block for connect
                this.socketChannel.socket().connect(new InetSocketAddress(this.serverIp, this.serverPort), 5000);
                this.socketChannel.socket().setTcpNoDelay(true);
                this.socketChannel.socket().setSendBufferSize(8192);
                this.socketChannel.socket().setSoTimeout(2000);
                this.socketChannel.configureBlocking(false); // Switch to non-blocking for writes!
                
                Log.d(TAG, "Connected!");
                Thread thread = new Thread(new Runnable(){

                    @Override
                    public void run() {
                        MaimaiTouchClient.this.readLoop();
                    }
                });
                thread.start();
                
                ByteBuffer objectBuffer = ByteBuffer.allocate(8);
                objectBuffer.order(ByteOrder.LITTLE_ENDIAN);
                
                long lastSentState = -1L;
                
                while (this.isRunning && this.socketChannel.isConnected()) {
                    long l;
                    Object object = this.lock;
                    synchronized (object) {
                        l = this.currentState;
                    }
                    
                    // Optimization: you might only want to send if state changed, but sending constantly is fine at 240Hz.
                    objectBuffer.clear();
                    objectBuffer.putLong(l);
                    objectBuffer.flip();
                    try {
                        // In non-blocking mode, this will return 0 if buffer is full, essentially dropping the frame!
                        this.socketChannel.write(objectBuffer);
                    }
                    catch (Exception e) {
                        // write error
                        break;
                    }
                    Thread.sleep(4L, 166666);
                }
            }
            catch (Exception exception) {
                Log.e(TAG, "Network error: " + exception.getMessage());
            }
            finally {
                try {
                    if (this.socketChannel != null) {
                        this.socketChannel.close();
                    }
                }
                catch (Exception exception) {}
                this.isDiagnostic = false;
                if (this.diagnosticListener != null) {
                    this.diagnosticListener.onDisconnected();
                }
            }
            if (!this.isRunning) continue;
            try {
                Thread.sleep(1000L);
            }
            catch (InterruptedException interruptedException) {}
        }
    }

    private void readLoop() {
        try {
            ByteBuffer readBuffer = ByteBuffer.allocate(8);
            readBuffer.order(ByteOrder.LITTLE_ENDIAN);
            
            while (this.isRunning && this.socketChannel.isConnected()) {
                readBuffer.clear();
                int n = 0;
                try {
                    n = this.socketChannel.read(readBuffer);
                } catch (Exception e) {
                    break;
                }
                
                if (n > 0) {
                    byte[] byArray = readBuffer.array();
                    if (n >= 4 && byArray[0] == 68 && byArray[1] == 73 && byArray[2] == 65 && byArray[3] == 71) {
                        this.isDiagnostic = true;
                        if (this.diagnosticListener == null) continue;
                        this.diagnosticListener.onDiagnosticConnected();
                        continue;
                    }
                    if (n >= 8 && byArray[0] == 0x47 && byArray[1] == 0x41 && byArray[2] == 0x4D && byArray[3] == 0x45) {
                        boolean isInGame = byArray[7] == 1;
                        if (this.diagnosticListener != null) {
                            this.diagnosticListener.onGameStatusChanged(isInGame);
                        }
                        continue;
                    }
                    if (n == 8) {
                        readBuffer.flip();
                        long l = readBuffer.getLong();
                        if (l == -1229782938247303442L && this.lastPingTime > 0L) {
                            long l2 = System.currentTimeMillis() - this.lastPingTime;
                            this.lastPingTime = 0L;
                            if (this.diagnosticListener != null) {
                                this.diagnosticListener.onLatencyResult(l2);
                            }
                        }
                    }
                } else if (n == -1) {
                    break;
                } else {
                    Thread.sleep(10); // sleep to prevent 100% CPU when no data
                }
            }
        }
        catch (Exception exception) {
            Log.e(TAG, "Read loop error: " + exception.getMessage());
        }
        finally {
            try {
                if (this.socketChannel != null) {
                    this.socketChannel.close();
                }
            }
            catch (Exception exception2) {}
        }
    }

    public void testLatency() {
        if (!this.isDiagnostic || this.socketChannel == null) {
            return;
        }
        new Thread(new Runnable(){

            @Override
            public void run() {
                try {
                    MaimaiTouchClient.this.lastPingTime = System.currentTimeMillis();
                    ByteBuffer byteBuffer = ByteBuffer.allocate(8);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    byteBuffer.putLong(-1229782938247303442L);
                    byteBuffer.flip();
                    MaimaiTouchClient.this.socketChannel.write(byteBuffer);
                }
                catch (Exception exception) {
                    Log.e(MaimaiTouchClient.TAG, "Ping error: " + exception.getMessage());
                }
            }
        }).start();
    }

    public void setButtonState(String string, boolean bl) {
        Integer n = BUTTON_MAP.get(string);
        if (n != null) {
            Object object = this.lock;
            synchronized (object) {
                this.currentState = bl ? (this.currentState | (1L << n)) : (this.currentState & ~(1L << n));
            }
        }
    }

    public void disconnect() {
        this.isRunning = false;
        try {
            if (this.socketChannel != null) {
                this.socketChannel.close();
            }
        }
        catch (Exception exception) {
            Log.e(TAG, "Disconnect error: " + exception.getMessage());
        }
    }

    static {
        BUTTON_MAP.put("Btn1", 0);
        BUTTON_MAP.put("Btn2", 1);
        BUTTON_MAP.put("Btn3", 2);
        BUTTON_MAP.put("Btn4", 3);
        BUTTON_MAP.put("Btn5", 4);
        BUTTON_MAP.put("Btn6", 5);
        BUTTON_MAP.put("Btn7", 6);
        BUTTON_MAP.put("Btn8", 7);
        BUTTON_MAP.put("A1", 8);
        BUTTON_MAP.put("A2", 9);
        BUTTON_MAP.put("A3", 10);
        BUTTON_MAP.put("A4", 11);
        BUTTON_MAP.put("A5", 12);
        BUTTON_MAP.put("A6", 13);
        BUTTON_MAP.put("A7", 14);
        BUTTON_MAP.put("A8", 15);
        BUTTON_MAP.put("B1", 16);
        BUTTON_MAP.put("B2", 17);
        BUTTON_MAP.put("B3", 18);
        BUTTON_MAP.put("B4", 19);
        BUTTON_MAP.put("B5", 20);
        BUTTON_MAP.put("B6", 21);
        BUTTON_MAP.put("B7", 22);
        BUTTON_MAP.put("B8", 23);
        BUTTON_MAP.put("C1", 24);
        BUTTON_MAP.put("C2", 25);
        BUTTON_MAP.put("D1", 26);
        BUTTON_MAP.put("D2", 27);
        BUTTON_MAP.put("D3", 28);
        BUTTON_MAP.put("D4", 29);
        BUTTON_MAP.put("D5", 30);
        BUTTON_MAP.put("D6", 31);
        BUTTON_MAP.put("D7", 32);
        BUTTON_MAP.put("D8", 33);
        BUTTON_MAP.put("E1", 34);
        BUTTON_MAP.put("E2", 35);
        BUTTON_MAP.put("E3", 36);
        BUTTON_MAP.put("E4", 37);
        BUTTON_MAP.put("E5", 38);
        BUTTON_MAP.put("E6", 39);
        BUTTON_MAP.put("E7", 40);
        BUTTON_MAP.put("E8", 41);
        BUTTON_MAP.put("Select", 42);
        BUTTON_MAP.put("Test", 43);
        BUTTON_MAP.put("Service", 44);
        BUTTON_MAP.put("Coin", 45);
    }

    public static interface OnDiagnosticListener {
        public void onDiagnosticConnected();
        public void onLatencyResult(long var1);
        public void onDisconnected();
        public void onGameStatusChanged(boolean isInGame);
    }
}
