package server;

import service.ServiceMessages;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private boolean authenticated;
    private String nickname;
    private String login;

    public ClientHandler(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;

        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    socket.setSoTimeout(120000);
                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.equals(ServiceMessages.EXIT)) {
                            sendMsg(ServiceMessages.EXIT);
                            break;
                        }
//                        if (str.startsWith("/auth")) {
                        if (str.startsWith(ServiceMessages.AUTH)) {
                            String[] token = str.split(" ", 3);
                            if (token.length < 3) {
                                continue;
                            }
                            String newNick = server.getAuthService()
                                    .getNicknameByLoginAndPassword(token[1], token[2]);
                            login = token[1];
                            if (newNick != null) {
                                if (!server.isLoginAuthenticated(login)) {
                                    authenticated = true;
                                    nickname = newNick;
                                    sendMsg(ServiceMessages.AUTH_OK + " " + nickname);
                                    server.subscribe(this);
                                    System.out.println("Client: " + nickname + " authenticated");
                                    socket.setSoTimeout(0);
                                    server.broadcastEnter(this);
                                    break;
                                } else {
                                    sendMsg("Пользователь с таким логином уже зашел в чат!");
                                }
                            } else {
                                sendMsg("Неверный логин / пароль");
                            }
                        }
                        if (str.startsWith(ServiceMessages.REGISTRATION)) {
                            String[] token = str.split(" ", 4);
                            if (token.length < 4) {
                                continue;
                            }
                            if (server.getAuthService()
                                    .registration(token[1], token[2], token[3])) {
                                sendMsg("/reg_ok");
                            } else {
                                sendMsg("/reg_no");
                            }
                        }
                    }
                    //цикл работы
                    while (authenticated) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals(ServiceMessages.EXIT)) {
                                sendMsg(ServiceMessages.EXIT);
                                server.broadcastExit(this);
                                break;
                            }
                            if (str.startsWith(ServiceMessages.PRIVATE_MESSAGE)) {
                                String[] token = str.split(" ", 3);
                                if (token.length < 3) {
                                    continue;
                                }
                                server.privateMsg(this, token[1], token[2]);
                            }

                        } else {
                            server.broadcastMsg(this, str);
                        }
                    }

                    //SocketTimeoutException
                } catch (SocketTimeoutException e) {
                    sendMsg(ServiceMessages.EXIT);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    System.out.println("Client disconnect!");
                    server.unsubscribe(this);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNickname() {
        return nickname;
    }

    public String getLogin() {
        return login;
    }
}
