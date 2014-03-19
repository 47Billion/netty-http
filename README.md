http
====
A library to develop HTTP services with Netty. Supports capabilities to route end-points based JAX-RS style
annotations. Implements Guava's Service interface to manage the runtime-state of the HTTP service.

Need for this framework
-----------------------
Netty is a powerful framework to write asynchronous event-driven high-performance applications. While it is
relatively easy to write a RESTFUL HTTP service using netty, the mapping between HTTP routes to handlers is
not a straight-forward task.

Mapping the routes to method handlers requires writing custom channel handlers and a lot of boilerplate code
as well as knowledge of Netty's internals in order to correctly chain different handlers. The mapping could be
error prone and tedious when a service handles a lot of end-points.

This library solves these problems using JAX-RS annotations to build a path routing layer on top of Netty.

Setting up an HTTP Service using the framework
----------------------------------------------
Setting up an HTTP service is very simple using this framework:
* Implement handler methods for different HTTP requests
* Annotate the routes for each handler
* Use a builder to setup the HTTP service

Example: A simple HTTP service that responds to the `/v1/ping` endpoint can be setup as:

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

References
----------
* [Guava][https://code.google.com/p/guava-libraries/]
* [Jersey][https://jersey.java.net]
* [Netty][http://netty.io/]