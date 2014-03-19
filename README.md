http
====
Framework to build HTTP service on top of Netty. Supports capabilities to route end-points based on annotations.
Implements Guava's Service interface to manage the runtime-state of the HTTP service.

Need for this framework
-----------------------
Netty is a powerful framework to write asynchronous event-driven high-performance applications. While it is
relatively easy to write a RESTFUL HTTP service using netty, the mapping between HTTP routes to handlers is
not a straight-forward task.

Mapping the routes to method handlers requires writing custom `ChannelHandler`s and a lot of boilerplate code
as well as knowledge of Netty's internals in order to correctly chain different handlers.The mapping could be
error prone and tedious when a service handles a lot of end-points.

This can be simplified by using annotations on HTTP routes handling particular
end-points and having a framework that can resolve the path routing based on these annotations.

This framework aims to solve these problems by using JAX-RS annotations from the jersey-library for HTTP path
routing to handle mapping and by building a path routing layer on top of the Netty HTTP.

Setting up an HTTP Service using the framework
----------------------------------------------
Setting up an HTTP service is very simple using this framework:
a) Write Handler methods for different HTTP requests
b) Annotate the routes for each handler
c) Use a builder to setup the HTTP service

Example: A simple HTTP service that responds to `/v1/ping` endpoint can be setup as:

```java
    // Set up Handlers for Ping
    public class PingHandler extends AbstractHttpHandler {
      @Path("/v1/ping")
      @GET
      public void testGet(HttpRequest request, HttpResponder responder){
        responder.sendString(HttpResponseStatus.OK, "OK");
      }
    }

    // Setup HTTP service and add Handlers
    NettyHttpService service = NettyHttpService.builder()
                               .setPort(7777)
                               .addHttpHandlers(ImmutableList.of(new PingHandler()))
                               .build();

    // Start the HTTP service
    server.startAndWait();
```