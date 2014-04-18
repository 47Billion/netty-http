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

import com.google.common.base.Preconditions;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * HttpDispatcher that routes HTTP requests to appropriate http handler methods. The mapping between uri paths to
 * methods that handle particular path is managed using jax-rs annotations. {@code HttpMethodHandler} routes to method
 * whose @Path annotation matches the http request uri.
 */
public class HttpDispatcher extends SimpleChannelUpstreamHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HttpDispatcher.class);
  private final HttpResourceHandler httpMethodHandler;
  BodyConsumer streamer;
  private boolean keepAlive;

  public HttpDispatcher(HttpResourceHandler methodHandler) {
    this.httpMethodHandler = methodHandler;
    this.keepAlive = true;
    this.streamer = null;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    Object message = e.getMessage();
    Channel channel = ctx.getChannel();
    Method destHandler = NettyHttpService.invokeMethod.get(ctx.getChannel());

    if(destHandler.getReturnType().equals(BodyConsumer.class)){
      if( (message instanceof HttpMessage) || (message instanceof HttpRequest)){
        Object msg = e.getMessage();
        HttpMessage httpMessage = (HttpMessage) msg;
        this.keepAlive = HttpHeaders.isKeepAlive(httpMessage);
        this.streamer = (BodyConsumer) destHandler.invoke(NettyHttpService.invokeHandler.get(channel),
                                                                  NettyHttpService.invokeArgs.get(channel));
        this.streamer.chunk(((HttpMessage) msg).getContent(),
                       new BasicHttpResponder(channel, HttpHeaders.isKeepAlive(httpMessage)));
      }
    else if (message instanceof HttpChunk) {
          Object msg = e.getMessage();
          HttpChunk httpChunk = (HttpChunk) msg;
          if(httpChunk.isLast()){
            this.streamer.finished(new BasicHttpResponder(channel,this.keepAlive));
          }
        else {
            this.streamer.chunk(httpChunk.getContent(), new BasicHttpResponder(channel, this.keepAlive));
          }
        }
    }
    else {
      if(message instanceof HttpRequest){
        destHandler.invoke(NettyHttpService.invokeHandler.get(channel), NettyHttpService.invokeArgs.get(channel));
      }
      else {
          super.messageReceived(ctx, e);
          return;
      }
    }
    //handleRequest((HttpRequest) message, ctx.getChannel());
  }

  /*private void handleRequest(HttpRequest httpRequest, Channel channel) {
    //Preconditions.checkNotNull(httpMethodHandler, "Http Handler factory cannot be null");
    //httpMethodHandler.handle(httpRequest, new BasicHttpResponder(channel, HttpHeaders.isKeepAlive(httpRequest)),channel);
    //channel.getPipeline().getContext("")
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    LOG.error("Exception caught in channel processing.", e.getCause());
    ctx.getChannel().close();
  }*/
}
