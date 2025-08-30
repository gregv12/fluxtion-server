# How to subscribe to specific named EventFeeds (and ignore others)

This guide shows how to subscribe to multiple EventFeeds by name using several named InMemoryEventSource inputs, and ignore any feeds whose names don’t match.

Key ideas:
- Each EventFeed has a name (set in EventFeedConfig.name("..."))
- By default, items published by feeds are wrapped as NamedFeedEvent during subscription-mode delivery
- Your processor can filter by feed name and only act on the ones you care about

Below we create three in-memory feeds: prices, orders, news. Our processor forwards only prices and news items to a sink, ignoring orders entirely.

## Sample code

Test source: src/test/java/com/fluxtion/server/example/NamedFeedsSubscriptionExampleTest.java

Processor node: src/test/java/com/fluxtion/server/example/NamedFeedsFilterHandler.java

### Processor handler that filters by feed name

public class NamedFeedsFilterHandler extends ObjectEventHandlerNode {
    private final Set<String> acceptedFeedNames;
    private MessageSink sink;

    public NamedFeedsFilterHandler(Set<String> acceptedFeedNames) {
        this.acceptedFeedNames = acceptedFeedNames;
    }

    @ServiceRegistered
    public void wire(MessageSink sink, String name) {
        this.sink = sink;
    }

    @Override
    protected boolean handleEvent(Object event) {
        if (sink == null || event == null) return true;
        if (event instanceof NamedFeedEvent<?> nfe) {
            // Check the feed name and forward only when it matches one of our accepted names
            String feedName;
            try {
                feedName = (String) nfe.getClass().getMethod("feedName").invoke(nfe);
            } catch (Exception e1) {
                try { feedName = (String) nfe.getClass().getMethod("getFeedName").invoke(nfe); }
                catch (Exception e2) {
                    try { feedName = (String) nfe.getClass().getMethod("name").invoke(nfe); }
                    catch (Exception e3) { feedName = null; }
                }
            }
            if (feedName != null && acceptedFeedNames.contains(feedName)) {
                Object data = nfe.data();
                if (data != null) {
                    sink.accept(data.toString());
                }
            }
        }
        return true;
    }
}

Note: NamedFeedEvent exposes data(); the accessor for the feed name may vary by version. The sample above uses a small reflective helper to read one of feedName(), getFeedName(), or name().

### Wiring several named InMemoryEventSource feeds

- Build the sink (FileMessageSink in this example)
- Create three InMemoryEventSource instances
- Register three EventFeedConfig entries with names: prices, orders, news
- Add the filter processor and the sink to AppConfig
- Boot the server and send events

Snippet from the test setup:

InMemoryEventSource<String> prices = new InMemoryEventSource<>();
prices.setCacheEventLog(true);
InMemoryEventSource<String> orders = new InMemoryEventSource<>();
orders.setCacheEventLog(true);
InMemoryEventSource<String> news = new InMemoryEventSource<>();
news.setCacheEventLog(true);

NamedFeedsFilterHandler filterHandler = new NamedFeedsFilterHandler(Set.of("prices", "news"));

EventProcessorGroupConfig processorGroup = EventProcessorGroupConfig.builder()
    .agentName("processor-agent")
    .put("filter-processor", new EventProcessorConfig(filterHandler))
    .build();

EventFeedConfig<?> pricesFeed = EventFeedConfig.builder()
    .instance(prices)
    .name("prices")
    .broadcast(true)
    .agent("prices-agent", new BusySpinIdleStrategy())
    .build();

EventFeedConfig<?> ordersFeed = EventFeedConfig.builder()
    .instance(orders)
    .name("orders")
    .broadcast(true)
    .agent("orders-agent", new BusySpinIdleStrategy())
    .build();

EventFeedConfig<?> newsFeed = EventFeedConfig.builder()
    .instance(news)
    .name("news")
    .broadcast(true)
    .agent("news-agent", new BusySpinIdleStrategy())
    .build();

EventSinkConfig<FileMessageSink> sinkCfg = EventSinkConfig.<FileMessageSink>builder()
    .instance(fileSink)
    .name("fileSink")
    .build();

AppConfig appConfig = AppConfig.builder()
    .addProcessorGroup(processorGroup)
    .addEventFeed(pricesFeed)
    .addEventFeed(ordersFeed)
    .addEventFeed(newsFeed)
    .addEventSink(sinkCfg)
    .build();

Boot, obtain services by name, and publish some events:

FluxtionServer server = FluxtionServer.bootServer(appConfig, rec -> {});
Map<String, com.fluxtion.runtime.service.Service<?>> services = server.registeredServices();
InMemoryEventSource<String> pricesSvc = (InMemoryEventSource<String>) services.get("prices").instance();
InMemoryEventSource<String> ordersSvc = (InMemoryEventSource<String>) services.get("orders").instance();
InMemoryEventSource<String> newsSvc   = (InMemoryEventSource<String>) services.get("news").instance();

pricesSvc.offer("p1");
ordersSvc.offer("o1");
newsSvc.offer("n1");

Only p1 and n1 will be forwarded by the processor; o1 is ignored because its feed name is not in the accepted set.

## Alternative: use NamedFeedEventHandlerNode
If you prefer declarative subscription by feed name, you can also use the runtime NamedFeedEventHandlerNode with a filter string (e.g. new NamedFeedEventHandlerNode<>("prices", "id")) and connect it into your processing graph. The example above illustrates a simple approach without needing code generation, suitable for small samples or tests.
