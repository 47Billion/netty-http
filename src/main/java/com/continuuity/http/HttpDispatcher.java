/**
 * Copyright 2012-2014 Continuuity, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.continuuity.http;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpDispatcher that invokes the appropriate http-handler method. The handler and the arguments are read
 * from the {@code RequestRouter} context.
 */

public class HttpDispatcher extends SimpleChannelUpstreamHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HttpDispatcher.class);

  private BodyConsumer streamer;
  private boolean keepAlive;

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
    HttpMethodInfo methodInfo  = (HttpMethodInfo) ctx.getPipeline().getContext("router").getAttachment();
    try {
      Object message = e.getMessage();
      Channel channel = ctx.getChannel();
      if (methodInfo.isStreaming()) {
        //streaming
        if (message instanceof HttpMessage) {
          HttpMessage httpMessage = (HttpMessage) message;
          this.keepAlive = HttpHeaders.isKeepAlive(httpMessage);
          this.streamer = methodInfo.invokeStreamingMethod();
          this.streamer.chunk(httpMessage.getContent(),
                              new BasicHttpResponder(channel, HttpHeaders.isKeepAlive(httpMessage)));
          if (!httpMessage.isChunked()){
            // Message is not chunked, this is the only packet, we should call finish now and set streamer to null.
            this.streamer.finished(new BasicHttpResponder(channel, HttpHeaders.isKeepAlive(httpMessage)));
            this.streamer = null;
          }
        } else if (message instanceof HttpChunk) {
          HttpChunk httpChunk = (HttpChunk) message;
          if (this.streamer == null){
            // Received a chunk not belonging to a HttpMessage.
            throw new IllegalStateException(
              "received " + HttpChunk.class.getSimpleName() +
                " without " + HttpMessage.class.getSimpleName());
          }
          if (httpChunk.isLast()) {
            //last chunk, end of streaming.
            this.streamer.finished(new BasicHttpResponder(channel, this.keepAlive));
            this.streamer = null;
          } else {
            this.streamer.chunk(httpChunk.getContent(), new BasicHttpResponder(channel, this.keepAlive));
          }
        } else {
          super.messageReceived(ctx, e);
        }
      } else {
        // http method without streaming. typical invocation.
        if (message instanceof HttpRequest) {
          methodInfo.invoke();
        } else {
          super.messageReceived(ctx, e);
        }
      }
    } catch (Exception ex) {
      methodInfo.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                           String.format("Error in executing path:"));
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    LOG.error("Exception caught in channel processing.", e.getCause());
    ctx.getChannel().close();
  }
}
