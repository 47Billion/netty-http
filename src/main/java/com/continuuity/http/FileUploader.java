package com.continuuity.http;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 *
 */
public class FileUploader extends Thread {

  NettyHttpService service;
  private static final java.lang.reflect.Type MAP_STRING_STRING_TYPE
    = new TypeToken<Map<String, String>>() { }.getType();
  static ChannelPipeline pipeline;

  /**
   * Json serializer.
   */
  private static final Gson GSON = new Gson();


  public static ChannelPipeline getPipeline(){
    return pipeline;
  }
  public void run() {
    String hostname = "127.0.0.1";
    int httpPort = 45001;
    service = NettyHttpService.builder()
      .setHost(hostname)
      .setPort(httpPort)
      .addHttpHandlers(ImmutableList.of(new FileUploaderHandler()))
      .setConnectionBacklog(2000)
      .setExecThreadPoolSize(20)
      .setBossThreadPoolSize(1)
      .setWorkerThreadPoolSize(10)
      .build();
    service.startAndWait();

    pipeline = service.getPipeline();

    System.out.print(service.isRunning());

  }

  public static void main(String args[]){
    FileUploader upper;
    upper = new FileUploader();
    upper.run();
    while (true){
      try {
        Thread.sleep(100);
      } catch (Exception ex){
        ex.printStackTrace();
      }

    }

  }

  class FileUploaderHandler implements HttpHandler {
    private static final int MAX_INPUT_SIZE = 1024 * 1024 * 1024;
    private static final int CHUNK_MEMORY_LIMIT = 1024 * 1024;

    @Path("/upload")
    @PUT
    public BodyConsumer upload(HttpRequest request, HttpResponder responder){

      return new BodyConsumer() {
        ByteBuffer offHeapBuffer = ByteBuffer.allocateDirect(MAX_INPUT_SIZE);

        @Override
        public void chunk(ChannelBuffer request, HttpResponder responder) {
          offHeapBuffer.put(request.array());
        }

        @Override
        public void finished(HttpResponder responder) {
          int bytesUploaded = offHeapBuffer.position();
          responder.sendString(HttpResponseStatus.OK, "Uploaded " + bytesUploaded + " bytes");
          return;
        }
      };
    }

    @Path("/upload2")
    @PUT
    public void upload2(HttpRequest request, HttpResponder response){
      int size = request.getContent().readableBytes();
      response.sendString(HttpResponseStatus.OK, "Upload complete.. chunked size :" + size + "bytes");
    }

    @Path("/ping")
    @GET
    public void Get(HttpRequest request, HttpResponder response){
      response.sendString(HttpResponseStatus.OK, "OK");
    }

    public String getPath(HttpRequest request){
      Map<String, String> args;
      Reader reader = new InputStreamReader(new ChannelBufferInputStream(request.getContent()), Charsets.UTF_8);
      ChannelBuffer content = request.getContent();
      if (!content.readable()) {
        return null;
      }
      try {
        args = GSON.fromJson(reader, MAP_STRING_STRING_TYPE);
        if (args == null)
          return null;
        else{
          if (!args.isEmpty()) {
            return args.get("path");
          }
        }
      } catch (JsonSyntaxException e) {
        e.printStackTrace();
        throw e;
      } finally {
        try {
          reader.close();
        } catch (IOException ex){
          ex.printStackTrace();
        }
      }
      return null;
    }
    @Override
    public void init(HandlerContext context) {

    }

    @Override
    public void destroy(HandlerContext context) {

    }
  }

}
