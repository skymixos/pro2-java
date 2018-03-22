package org.nanohttpd.protocols.http;

/*
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2016 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;

import org.nanohttpd.protocols.http.tempfiles.ITempFileManager;

/**
 * ClientHandler - 处理新客户端连接类
 * 请求处理策略类会利用该类构建一个客户端连接处理线程来处理每个HTTP请求
 */
public class ClientHandler implements Runnable {

    private final NanoHTTPD httpd;			/* http服务器对象 */
    private final InputStream inputStream;	/* 客户端的输入流 */
    private final Socket acceptSocket;		/* 建立连接的客户端 */

    public ClientHandler(NanoHTTPD httpd, InputStream inputStream, Socket acceptSocket) {
        this.httpd = httpd;
        this.inputStream = inputStream;
        this.acceptSocket = acceptSocket;
    }

    public void close() {
        NanoHTTPD.safeClose(this.inputStream);
        NanoHTTPD.safeClose(this.acceptSocket);
    }

    
    @Override
    public void run() {
        OutputStream outputStream = null;
        try {
            outputStream = this.acceptSocket.getOutputStream();		/* 得到该套接字对应的输出流（用于发送响应） */
            ITempFileManager tempFileManager = httpd.getTempFileManagerFactory().create();
            
            /* 构造一个http会话对象 */
            HTTPSession session = new HTTPSession(httpd, tempFileManager, this.inputStream, outputStream, this.acceptSocket.getInetAddress());
            
            while (!this.acceptSocket.isClosed()) {
                session.execute();	/* 调用HTTPSession.execute来处理客户端的请求 */
            }
        } catch (Exception e) {
            // When the socket is closed by the client,
            // we throw our own SocketException
            // to break the "keep alive" loop above. If
            // the exception was anything other
            // than the expected SocketException OR a
            // SocketTimeoutException, print the
            // stacktrace
            if (!(e instanceof SocketException && "NanoHttpd Shutdown".equals(e.getMessage())) && !(e instanceof SocketTimeoutException)) {
                NanoHTTPD.LOG.log(Level.SEVERE, "Communication with the client broken, or an bug in the handler code", e);
            }
        } finally {
            NanoHTTPD.safeClose(outputStream);			/* 关闭客户端的输出流 */
            NanoHTTPD.safeClose(this.inputStream);		/* 关闭客户端的输入流 */
            NanoHTTPD.safeClose(this.acceptSocket);		/* 关闭客户端套接字  */
            httpd.asyncRunner.closed(this);				/* 异步处理策略列表中移除该ClinetHandler对象 */
        }
    }
}
