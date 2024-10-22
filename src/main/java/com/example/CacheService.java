package com.example;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;

import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.*;

import java.net.*;

import java.util.*;

public class CacheService {

    private static int httpPort = 8080;
    private static Map<String, Object> dataCache = new HashMap<>();

    public static void main(String[] args) {
        new Thread(new ThreadHttp(httpPort, dataCache)).start();
        new Thread(new ThreadSocket(Integer.parseInt(args[0]), dataCache)).start();
    }
}

class ThreadSocket implements Runnable{

    private int selfSocPort;
    private Map<String, Object> dataCache;

    ThreadSocket(int selfSocPort, Map<String, Object> dataCache){
        this.selfSocPort = selfSocPort;
        this.dataCache = dataCache;
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(this.selfSocPort);
            while(true){
                Socket socket = serverSocket.accept();
                DataInputStream in = new DataInputStream(socket.getInputStream());
                String dataGet = in.readUTF();
                String methodStr = dataGet.substring(0, 1);
                if("0".equals(methodStr)){
                    JSONObject jsonObject = JSONObject.parseObject(dataGet.substring(1));
                    for(String key : jsonObject.keySet()){
                        dataCache.put(key, jsonObject.get(key));
                    }
                }else if("1".equals(methodStr)){
                    dataCache.remove(dataGet.substring(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class CacheServer{
    private Map<String, Object> dataCache;
    private int[] ports = {8887, 8888, 8889};
    private String[] ips = {"192.168.0.2", "192.168.0.3", "192.168.0.4"};

    CacheServer(Map<String, Object> dataCache){
        this.dataCache = dataCache;
    }

    public String searchKV(String keyGet){
        String result = null;
        if(dataCache.containsKey(keyGet)){
            Object re = dataCache.get(keyGet);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(keyGet, re);
            result = jsonObject.toString();
        }
        return result;
    }

    public void updateKV(JSONObject jsonObject) throws UnknownHostException, IOException{
        addData(jsonObject);
        for(int i=0; i<3; i++){
            Socket clientSocket = new Socket(ips[i], ports[i]);
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            out.writeUTF("0" + jsonObject.toString());
            out.close();
            clientSocket.close();
        }
    }

    public void deleteKV(String keyGet) throws UnknownHostException, IOException{
        dataCache.remove(keyGet);
        for(int i=0; i<3; i++){
            Socket clientSocket = new Socket(ips[i], ports[i]);
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            out.writeUTF("1" + keyGet);
            clientSocket.close();
        }
    }

    public void addData(JSONObject jsonObject){
        for(String key : jsonObject.keySet()){
            if(!dataCache.containsKey(key)){
                dataCache.put(key, jsonObject.get(key));
            }
        }
    }
}

class ThreadHttp implements Runnable{
    private int httpPort; //port listened
    private Map<String, Object> dataCache;//data store

    ThreadHttp(int httpPort, Map<String, Object> dataCache){
        this.httpPort = httpPort;
        this.dataCache = dataCache;
    }

    @Override
    public void run() {
        HttpServer httpServer;
        try {
            httpServer = HttpServer.create(new InetSocketAddress(httpPort), 10);
            httpServer.createContext("/", new MeHandler(dataCache));
            httpServer.setExecutor(null);
            httpServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


//MeHandler Class to deal with different methods of http request
class MeHandler implements HttpHandler{

    private Map<String, Object> dataCache;
    MeHandler(Map<String, Object> dataCache){
        this.dataCache = dataCache;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        //get request method
        String methodString = exchange.getRequestMethod();
        if("POST".equals(methodString)){
            CacheServer cacheServer = new CacheServer(dataCache);
            InputStream in = exchange.getRequestBody();
            byte[] b = new byte[1024];
            int length = 0;
            String getData = null;
            while((length = in.read(b)) > 0){
                getData = new String(b, 0, length);
                JSONObject jsonObject = JSONObject.parseObject(getData);
                cacheServer.updateKV(jsonObject);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, getData.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(getData.getBytes());
            os.close();
        }else if("GET".equals(methodString)){
            CacheServer cacheServer = new CacheServer(dataCache);
            String keyGet = exchange.getRequestURI().toString().substring(1);
            String dataGet = cacheServer.searchKV(keyGet);
            if(dataGet != null){
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, dataGet.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(dataGet.getBytes());
                os.close();
            }else{
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(404, 1);
                OutputStream os = exchange.getResponseBody();
                os.write(null);
                os.close();
            }
        }else if("DELETE".equals(methodString)){
            CacheServer cacheServer = new CacheServer(dataCache);
            String keyGet = exchange.getRequestURI().toString().substring(1);
            String dataGet = cacheServer.searchKV(keyGet);
            exchange.sendResponseHeaders(200, "1".length());
            OutputStream os = exchange.getResponseBody();
            if(dataGet != null){
                cacheServer.deleteKV(keyGet);
                os.write("1".getBytes());
            }else{
                os.write("0".getBytes());
            }
            os.close();
        }
    }
    
}