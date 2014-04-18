/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.continuuity.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DirectChannelBufferFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map.Entry;

import static org.jboss.netty.channel.Channels.succeededFuture;
import static org.jboss.netty.channel.Channels.write;
import static org.jboss.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;


/**
 * A {@link ChannelHandler} that aggregates an {@link HttpMessage}
 * and its following {@link HttpChunk}s into a single {@link HttpMessage} with
 * no following {@link HttpChunk}s.  It is useful when you don't want to take
 * care of HTTP messages whose transfer encoding is 'chunked'.  Insert this
 * handler after {HttpMessageDecoder} in the {@link ChannelPipeline}:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * ...
 * p.addLast("decoder", new { RequestRouter}());
 * p.addLast("aggregator", <b>new {@link HttpChunkFileAggregator}(1048576)</b>);
 * ...
 * p.addLast("encoder", new { HttpResponseEncoder}());
 * p.addLast("handler", new HttpRequestHandler());
 * </pre>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2354 $, $Date: 2010-08-26 14:06:40 +0900 (Thu, 26 Aug 2010) $
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.handler.codec.http.HttpChunk oneway - - filters out
 */
public class HttpChunkFileAggregator extends SimpleChannelUpstreamHandler {


  private HttpMessage currentMessage;
  private int memoryContentLength;
  private boolean msgInMemory=true;
  private ByteBuffer offHeapBuffer;
  private static final int MAX_INPUT_SIZE =  (2048 * 1024 * 1024)-1;
  private static final Logger LOG = LoggerFactory.getLogger(HttpChunkFileAggregator.class);

  private static final ChannelBuffer CONTINUE = ChannelBuffers.copiedBuffer(
    "HTTP/1.1 100 Continue\r\n\r\n", CharsetUtil.US_ASCII);


  /**
   * Creates a new instance.
   */

  public HttpChunkFileAggregator(int inMemoryLimit) {
    this.memoryContentLength = inMemoryLimit;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    throws Exception {

    Object msg = e.getMessage();
    HttpMessage currentMessage = this.currentMessage;
    if (msg instanceof HttpMessage) {
      HttpMessage m = (HttpMessage) msg;

      // Handle the 'Expect: 100-continue' header if necessary.
      // TODO: Respond with 413 Request Entity Too Large
      //   and discard the traffic or close the connection.
      //       No need to notify the upstream handlers - just log.
      //       If decoding a response, just throw an exception.
      if (is100ContinueExpected(m)) {
        write(ctx, succeededFuture(ctx.getChannel()), CONTINUE.duplicate());
      }


      if (m.isChunked()) {
        // A chunked message - remove 'Transfer-Encoding' header,
        // initialize the cumulative buffer, and wait for incoming chunks.
        List<String> encodings = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        encodings.remove(HttpHeaders.Values.CHUNKED);
        if (encodings.isEmpty()) {
          m.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
        }
        m.setChunked(false);
        m.setContent(ChannelBuffers.dynamicBuffer(e.getChannel().getConfig().getBufferFactory()));
        this.currentMessage = m;
      } else {
        // Not a chunked message - pass through.
        this.currentMessage = null;
        ctx.sendUpstream(e);
      }
    } else if (msg instanceof HttpChunk) {
      // Sanity check
      if (currentMessage == null) {
        throw new IllegalStateException(
          "received " + HttpChunk.class.getSimpleName() +
            " without " + HttpMessage.class.getSimpleName());
      }

      // Merge the received chunk into the content of the current message.

      HttpChunk chunk = (HttpChunk) msg;

      if(this.msgInMemory) {
        ChannelBuffer content = currentMessage.getContent();

        if (content.readableBytes() > memoryContentLength - chunk.getContent().readableBytes()) {
          //copy the content to Directbytebuffer and append the chunk content. set msgInMemory to false.
          try {
            offHeapBuffer = ByteBuffer.allocateDirect(MAX_INPUT_SIZE);
            content.resetReaderIndex();
            offHeapBuffer.put(content.array());
            chunk.getContent().resetReaderIndex();
            offHeapBuffer.put(chunk.getContent().array());
            this.msgInMemory = false;
          }
          catch (Exception ex){
            ex.printStackTrace();
          }
        }
        else {
          content.writeBytes(chunk.getContent());
        }
      }
      else{
        try {
          chunk.getContent().resetReaderIndex();
          offHeapBuffer.put(chunk.getContent().array());
        }
        catch (BufferOverflowException ex){
          throw new TooLongFrameException(
            "HTTP content length exceeded " + MAX_INPUT_SIZE +
              " bytes.");
        }
      }
      if (chunk.isLast()) {
        this.currentMessage = null;
        int position = offHeapBuffer.position();
        offHeapBuffer.rewind();
        offHeapBuffer.limit(position);

        // Merge trailing headers into the message.
        if (chunk instanceof HttpChunkTrailer) {
          HttpChunkTrailer trailer = (HttpChunkTrailer) chunk;
          for (Entry<String, String> header: trailer.getHeaders()) {
            currentMessage.setHeader(header.getKey(), header.getValue());
          }
        }

        // Set the 'Content-Length' header.
        if(msgInMemory) {
          currentMessage.setHeader(
            HttpHeaders.Names.CONTENT_LENGTH,
            String.valueOf(currentMessage.getContent().readableBytes()));
          // All done - generate the event.
          Channels.fireMessageReceived(ctx, currentMessage, e.getRemoteAddress());
        }
        else {
          currentMessage.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(offHeapBuffer.limit()));
          currentMessage.setContent(new DirectChannelBufferFactory().getBuffer(offHeapBuffer));
          Channels.fireMessageReceived(ctx, currentMessage, e.getRemoteAddress());
        }
      }
    } else {
      // Neither HttpMessage or HttpChunk
      ctx.sendUpstream(e);
    }
  }
}
