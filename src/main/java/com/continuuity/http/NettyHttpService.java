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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sun.tools.javac.util.Pair;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Webservice implemented using the netty framework. Implements Guava's Service interface to manage the states
 * of the webservice.
 */
public final class NettyHttpService extends AbstractIdleService {

  private static final Logger LOG  = LoggerFactory.getLogger(NettyHttpService.class);

  private static final int CLOSE_CHANNEL_TIMEOUT = 5;
  private final int bossThreadPoolSize;
  private final int workerThreadPoolSize;
  private final int execThreadPoolSize;
  private final long execThreadKeepAliveSecs;
  private final Map<String, Object> channelConfigs;
  private final RejectedExecutionHandler rejectedExecutionHandler;
  private final HandlerContext handlerContext;
  private final ChannelGroup channelGroup;
  private final HttpResourceHandler resourceHandler;
  private final Builder.BuilderHandlerCollection handlerCollection;


  private ServerBootstrap bootstrap;
  private InetSocketAddress bindAddress;
  private int httpChunkLimit;


  /**
   * Initialize NettyHttpService.
   * @param bindAddress Address for the service to bind to.
   * @param bossThreadPoolSize Size of the boss thread pool.
   * @param workerThreadPoolSize Size of the worker thread pool.
   * @param execThreadPoolSize Size of the thread pool for the executor.
   * @param execThreadKeepAliveSecs  maximum time that excess idle threads will wait for new tasks before terminating.
   * @param channelConfigs Configurations for the server socket channel.
   * @param rejectedExecutionHandler rejection policy for executor.
   * @param urlRewriter URLRewriter to rewrite incoming URLs.
   * @param httpHandlers HttpHandlers to handle the calls.
   * @param handlerHooks Hooks to be called before/after request processing by httpHandlers.
   * @param handlerCollection ChannelHandlers that are to be added to the Service's pipeline.
   */
  public NettyHttpService(InetSocketAddress bindAddress, int bossThreadPoolSize, int workerThreadPoolSize,
                          int execThreadPoolSize, long execThreadKeepAliveSecs,
                          Map<String, Object> channelConfigs,
                          RejectedExecutionHandler rejectedExecutionHandler, URLRewriter urlRewriter,
                          Iterable<? extends HttpHandler> httpHandlers,
                          Iterable<? extends HandlerHook> handlerHooks, int httpChunkLimit,
                          Builder.BuilderHandlerCollection handlerCollection) {
    this.bindAddress = bindAddress;
    this.bossThreadPoolSize = bossThreadPoolSize;
    this.workerThreadPoolSize = workerThreadPoolSize;
    this.execThreadPoolSize = execThreadPoolSize;
    this.execThreadKeepAliveSecs = execThreadKeepAliveSecs;
    this.channelConfigs = ImmutableMap.copyOf(channelConfigs);
    this.rejectedExecutionHandler = rejectedExecutionHandler;
    this.channelGroup = new DefaultChannelGroup();
    this.resourceHandler = new HttpResourceHandler(httpHandlers, handlerHooks, urlRewriter);
    this.handlerContext = new BasicHandlerContext(this.resourceHandler);
    this.httpChunkLimit = httpChunkLimit;
    this.handlerCollection = handlerCollection;
  }

  /**
   * Create Execution handlers with threadPoolExecutor.
   *
   * @param threadPoolSize size of threadPool
   * @param threadKeepAliveSecs  maximum time that excess idle threads will wait for new tasks before terminating.
   * @return instance of {@code ExecutionHandler}.
   */
  private ExecutionHandler createExecutionHandler(int threadPoolSize, long threadKeepAliveSecs) {

    ThreadFactory threadFactory = new ThreadFactory() {
      private final ThreadGroup threadGroup = new ThreadGroup("netty-executor-thread");
      private final AtomicLong count = new AtomicLong(0);

      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(threadGroup, r, String.format("executor-%d", count.getAndIncrement()));
        t.setDaemon(true);
        return t;
      }
    };

    //Create ExecutionHandler
    ThreadPoolExecutor threadPoolExecutor =
      new OrderedMemoryAwareThreadPoolExecutor(threadPoolSize, 0, 0,
                                               threadKeepAliveSecs, TimeUnit.SECONDS, threadFactory);
    threadPoolExecutor.setRejectedExecutionHandler(rejectedExecutionHandler);
    return new ExecutionHandler(threadPoolExecutor);
  }

  /**
   * Bootstrap the pipeline.
   * <ul>
   *   <li>Create Execution handler</li>
   *   <li>Setup Http resource handler</li>
   *   <li>Setup the netty pipeline</li>
   * </ul>
   *
   * @param threadPoolSize Size of threadpool in threadpoolExecutor
   * @param threadKeepAliveSecs  maximum time that excess idle threads will wait for new tasks before terminating.
   */
  private void bootStrap(int threadPoolSize, long threadKeepAliveSecs) {

    final ExecutionHandler executionHandler = (threadPoolSize) > 0 ?
      createExecutionHandler(threadPoolSize, threadKeepAliveSecs) : null;

    Executor bossExecutor = Executors.newFixedThreadPool(bossThreadPoolSize,
                                                         new ThreadFactoryBuilder()
                                                           .setDaemon(true)
                                                           .setNameFormat("netty-boss-thread")
                                                           .build());

    Executor workerExecutor = Executors.newFixedThreadPool(workerThreadPoolSize,
                                                           new ThreadFactoryBuilder()
                                                             .setDaemon(true)
                                                             .setNameFormat("netty-worker-thread")
                                                             .build());

    //Server bootstrap with default worker threads (2 * number of cores)
    bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(bossExecutor, bossThreadPoolSize,
                                                                      workerExecutor, workerThreadPoolSize));
    bootstrap.setOptions(channelConfigs);

    resourceHandler.init(handlerContext);

    final ChannelUpstreamHandler connectionTracker = new SimpleChannelUpstreamHandler() {
      @Override
      public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        channelGroup.add(e.getChannel());
        super.handleUpstream(ctx, e);
      }
    };

    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();

        for(Pair<String, ChannelHandler> pair : handlerCollection.getFirstHandlers()) {
          pipeline.addFirst(pair.fst, pair.snd);
        }

        pipeline.addLast("tracker", connectionTracker);
        pipeline.addLast("compressor", new HttpContentCompressor());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("router", new RequestRouter(resourceHandler, httpChunkLimit));
        if (executionHandler != null) {
          pipeline.addLast("executor", executionHandler);
        }
        pipeline.addLast("dispatcher", new HttpDispatcher());

        for(Pair<String, ChannelHandler> pair : handlerCollection.getLastHandlers()) {
          pipeline.addLast(pair.fst, pair.snd);
        }

        ArrayList<Builder.BuilderHandlerCollection.IntermediaryHandler> intermediaryHandlers =
                                                                          handlerCollection.getIntermediaryHandlers();
        for(Builder.BuilderHandlerCollection.IntermediaryHandler handler : intermediaryHandlers) {
          if(handler.getAction().equals("before")) {
            pipeline.addBefore(handler.getOtherHandlerName(), handler.getName(), handler.getHandler());
          } else {
            pipeline.addAfter(handler.getOtherHandlerName(), handler.getName(), handler.getHandler());
          }
        }

        return pipeline;
      }
    });
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting service on address {}...", bindAddress);
    bootStrap(execThreadPoolSize, execThreadKeepAliveSecs);
    Channel channel = bootstrap.bind(bindAddress);
    channelGroup.add(channel);
    bindAddress = ((InetSocketAddress) channel.getLocalAddress());
    LOG.info("Started service on address {}", bindAddress);
  }

  /**
   * @return port where the service is running.
   */
  public InetSocketAddress getBindAddress() {
    return bindAddress;
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Stopping service on address {}...", bindAddress);
    bootstrap.shutdown();
    try {
      if (!channelGroup.close().await(CLOSE_CHANNEL_TIMEOUT, TimeUnit.SECONDS)) {
        LOG.warn("Timeout when closing all channels.");
      }
    } finally {
      resourceHandler.destroy(handlerContext);
      bootstrap.releaseExternalResources();
    }
    LOG.info("Done stopping service on address {}", bindAddress);
  }

  /**
   * Builder to help create the NettyHttpService.
   */
  public static class Builder {

    /**
     * Maintain a record of all the ChannelHandlers added to the pipeline along with order of addition.
     */
    private class BuilderHandlerCollection {
      private ArrayList<Pair<String, ChannelHandler>> firstHandlers;
      private ArrayList<Pair<String, ChannelHandler>> lastHandlers;
      private ArrayList<IntermediaryHandler> intermediaryHandlers;

      private BuilderHandlerCollection() {
        firstHandlers = new ArrayList<Pair<String, ChannelHandler>>();
        lastHandlers = new ArrayList<Pair<String, ChannelHandler>>();
        intermediaryHandlers = new ArrayList<IntermediaryHandler>();
      }

      /**
       * Encapsulate information regarding location of the handler being added.
       * Maintains a record of what action to take (before/after) and the name of the handler which that action
       * is in relation to.
       */
      private class IntermediaryHandler {
        private String action;
        private String otherHandlerName;
        private String name;
        private ChannelHandler handler;

        private IntermediaryHandler(String action, String otherHandler, String name, ChannelHandler handler) {
          this.action = action;
          this.otherHandlerName = otherHandler;
          this.name = name;
          this.handler = handler;
        }

        private String getAction() {
          return action;
        }

        private String getOtherHandlerName() {
          return otherHandlerName;
        }

        private String getName() {
          return name;
        }

        private ChannelHandler getHandler() {
          return handler;
        }
      }

      private void addFirstHandler(String name, ChannelHandler handler) {
        firstHandlers.add(new Pair<String, ChannelHandler>(name, handler));
      }

      private void addLastHandler(String name, ChannelHandler handler) {
        lastHandlers.add(new Pair<String, ChannelHandler>(name, handler));
      }

      public ArrayList<Pair<String, ChannelHandler>> getLastHandlers() {
        return lastHandlers;
      }

      public ArrayList<Pair<String, ChannelHandler>> getFirstHandlers() {
        return firstHandlers;
      }

      private void addBeforeHandler(String before, String name, ChannelHandler handler) {
        intermediaryHandlers.add(new IntermediaryHandler("before", before, name, handler));
      }

      private void addAfterHandler(String after, String name, ChannelHandler handler) {
        intermediaryHandlers.add(new IntermediaryHandler("after", after, name, handler));
      }

      private ArrayList<IntermediaryHandler> getIntermediaryHandlers() {
        return intermediaryHandlers;
      }
    }

    private static final int DEFAULT_BOSS_THREAD_POOL_SIZE = 1;
    private static final int DEFAULT_WORKER_THREAD_POOL_SIZE = 10;
    private static final int DEFAULT_CONNECTION_BACKLOG = 1000;
    private static final int DEFAULT_EXEC_HANDLER_THREAD_POOL_SIZE = 60;
    private static final long DEFAULT_EXEC_HANDLER_THREAD_KEEP_ALIVE_TIME_SECS = 60L;
    private static final RejectedExecutionHandler DEFAULT_REJECTED_EXECUTION_HANDLER =
      new ThreadPoolExecutor.CallerRunsPolicy();
    private static final int DEFAULT_HTTP_CHUNK_LIMIT = 150 * 1024 * 1024;

    private Iterable<? extends HttpHandler> handlers;
    private Iterable<? extends HandlerHook> handlerHooks = ImmutableList.of();
    private URLRewriter urlRewriter = null;
    private int bossThreadPoolSize;
    private int workerThreadPoolSize;
    private int execThreadPoolSize;
    private String host;
    private int port;
    private long execThreadKeepAliveSecs;
    private RejectedExecutionHandler rejectedExecutionHandler;
    private Map<String, Object> channelConfigs;
    private int httpChunkLimit;
    private BuilderHandlerCollection handlerCollection;

    //Private constructor to prevent instantiating Builder instance directly.
    private Builder() {
      bossThreadPoolSize = DEFAULT_BOSS_THREAD_POOL_SIZE;
      workerThreadPoolSize = DEFAULT_WORKER_THREAD_POOL_SIZE;
      execThreadPoolSize = DEFAULT_EXEC_HANDLER_THREAD_POOL_SIZE;
      execThreadKeepAliveSecs = DEFAULT_EXEC_HANDLER_THREAD_KEEP_ALIVE_TIME_SECS;
      rejectedExecutionHandler = DEFAULT_REJECTED_EXECUTION_HANDLER;
      httpChunkLimit = DEFAULT_HTTP_CHUNK_LIMIT;
      port = 0;
      channelConfigs = Maps.newHashMap();
      channelConfigs.put("backlog", DEFAULT_CONNECTION_BACKLOG);
      handlerCollection = new BuilderHandlerCollection();
    }

    /**
     * Add a ChannelHandler to the end of the pipeline.
     * @param name Name of {@code ChannelHandler}.
     * @param handler Instance of {@code ChannelHandler}.
     * @return instance of {@code Builder}.
     */
    public Builder addLastChannelHandler(String name, ChannelHandler handler) {
      handlerCollection.addLastHandler(name, handler);
      return this;
    }

    /**
     * Add a ChannelHandler to the beginning of the pipeline.
     * @param name Name of {@code ChannelHandler}.
     * @param handler Instance of {@code ChannelHandler}.
     * @return instance of {@code Builder}.
     */
    public Builder addFirstChannelHandler(String name, ChannelHandler handler) {
      handlerCollection.addFirstHandler(name, handler);
      return this;
    }

    /**
     * Add a ChannelHandler to the pipeline before the named ChannelHandler.
     * @param before Name of ChannelHandler to add before.
     * @param name Name of {@code ChannelHandler}.
     * @param handler Instance of {@code ChannelHandler}.
     * @return Instance of {@code Builder}.
     */
    public Builder addBeforeChannelHandler(String before, String name, ChannelHandler handler) {
      handlerCollection.addBeforeHandler(before, name, handler);
      return this;
    }

    /**
     * Add a ChannelHandler to the pipeline after the named ChannelHandler.
     * @param after Name of ChannelHandler to add after.
     * @param name Name of {@code ChannelHandler}.
     * @param handler Instance of {@code ChannelHandler}.
     * @return Instance of {@code Builder}.
     */
    public Builder addAfterChannelHandler(String after, String name, ChannelHandler handler) {
      handlerCollection.addAfterHandler(after, name, handler);
      return this;
    }

    /**
     * Add HttpHandlers that service the request.
     *
     * @param handlers Iterable of HttpHandlers.
     * @return instance of {@code Builder}.
     */
    public Builder addHttpHandlers(Iterable<? extends HttpHandler> handlers) {
      this.handlers = handlers;
      return this;
    }

    /**
     * Set HandlerHooks to be executed pre and post handler calls. They are executed in the same order as specified
     * by the iterable.
     *
     * @param handlerHooks Iterable of HandlerHooks.
     * @return an instance of {@code Builder}.
     */
    public Builder setHandlerHooks(Iterable<? extends HandlerHook> handlerHooks) {
      this.handlerHooks = handlerHooks;
      return this;
    }

    /**
     * Set URLRewriter to re-write URL of an incoming request before any handlers or their hooks are called.
     *
     * @param urlRewriter instance of URLRewriter.
     * @return an instance of {@code Builder}.
     */
    public Builder setUrlRewriter(URLRewriter urlRewriter) {
      this.urlRewriter = urlRewriter;
      return this;
    }

    /**
     * Set size of bossThreadPool in netty default value is 1 if it is not set.
     *
     * @param bossThreadPoolSize size of bossThreadPool.
     * @return an instance of {@code Builder}.
     */
    public Builder setBossThreadPoolSize(int bossThreadPoolSize) {
      this.bossThreadPoolSize = bossThreadPoolSize;
      return this;
    }


    /**
     * Set size of workerThreadPool in netty default value is 10 if it is not set.
     *
     * @param workerThreadPoolSize size of workerThreadPool.
     * @return an instance of {@code Builder}.
     */
    public Builder setWorkerThreadPoolSize(int workerThreadPoolSize) {
      this.workerThreadPoolSize = workerThreadPoolSize;
      return this;
    }

    /**
     * Set size of backlog in netty service - size of accept queue of the TCP stack.
     *
     * @param connectionBacklog backlog in netty server. Default value is 1000.
     * @return an instance of {@code Builder}.
     */
    public Builder setConnectionBacklog(int connectionBacklog) {
      channelConfigs.put("backlog", connectionBacklog);
      return this;
    }

    /**
     * Sets channel configuration for the the netty service.
     *
     * @param key Name of the configuration.
     * @param value Value of the configuration.
     * @return an instance of {@code Builder}.
     * @see org.jboss.netty.channel.ChannelConfig
     * @see org.jboss.netty.channel.socket.ServerSocketChannelConfig
     */
    public Builder setChannelConfig(String key, Object value) {
      channelConfigs.put(key, value);
      return this;
    }

    /**
     * Set size of executorThreadPool in netty default value is 60 if it is not set.
     * If the size is {@code 0}, then no executor will be used, hence calls to {@link HttpHandler} would be made from
     * worker threads directly.
     *
     * @param execThreadPoolSize size of workerThreadPool.
     * @return an instance of {@code Builder}.
     */
    public Builder setExecThreadPoolSize(int execThreadPoolSize) {
      this.execThreadPoolSize = execThreadPoolSize;
      return this;
    }

    /**
     * Set threadKeepAliveSeconds -   maximum time that excess idle threads will wait for new tasks before terminating.
     * Default value is 60 seconds.
     *
     * @param threadKeepAliveSecs  thread keep alive seconds.
     * @return an instance of {@code Builder}.
     */
    public Builder setExecThreadKeepAliveSeconds(long threadKeepAliveSecs) {
      this.execThreadKeepAliveSecs = threadKeepAliveSecs;
      return this;
    }

    /**
     * Set RejectedExecutionHandler - rejection policy for executor.
     *
     * @param rejectedExecutionHandler rejectionExecutionHandler.
     * @return  an instance of {@code Builder}.
     */
    public Builder setRejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
      this.rejectedExecutionHandler = rejectedExecutionHandler;
      return this;
    }

    /**
     * Set the port on which the service should listen to.
     * By default the service will run on a random port.
     *
     * @param port port on which the service should listen to.
     * @return instance of {@code Builder}.
     */
    public Builder setPort(int port) {
      this.port = port;
      return this;
    }

    /**
     * Set the bindAddress for the service. Default value is localhost.
     *
     * @param host bindAddress for the service.
     * @return instance of {@code Builder}.
     */
    public Builder setHost(String host) {
      this.host = host;
      return this;
    }

    public Builder setHttpChunkLimit(int value) {
      this.httpChunkLimit = value;
      return this;
    }

    /**
     * @return instance of {@code NettyHttpService}
     */
    public NettyHttpService build() {
      InetSocketAddress bindAddress;
      if (host == null) {
        bindAddress = new InetSocketAddress("localhost", port);
      } else {
        bindAddress = new InetSocketAddress(host, port);
      }

      return new NettyHttpService(bindAddress, bossThreadPoolSize, workerThreadPoolSize,
                                  execThreadPoolSize, execThreadKeepAliveSecs, channelConfigs, rejectedExecutionHandler,
                                  urlRewriter, handlers, handlerHooks, httpChunkLimit, handlerCollection);
    }
  }
}
