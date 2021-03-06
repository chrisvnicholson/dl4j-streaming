package org.deeplearning4j.streaming.routes;

import kafka.serializer.StringEncoder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.net.util.Base64;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

/**
 * Serve results from a kafka queue.
 *
 * @author Adam Gibson
 */
@AllArgsConstructor
@Builder
public class DL4jServeRouteBuilder extends RouteBuilder {
    private String modelUri;
    private String kafkaBroker;
    private String consumingTopic;
    private boolean computationGraph;
    private String outputUri;
    private Processor finalProcessor;
    private String groupId = "dl4j-serving";
    private String zooKeeperHost = "localhost";
    private int zooKeeperPort = 2181;
    /**
     * <b>Called on initialization to build the routes using the fluent builder syntax.</b>
     * <p/>
     * This is a central method for RouteBuilder implementations to implement
     * the routes using the Java fluent builder syntax.
     *
     * @throws Exception can be thrown during configuration
     */
    @Override
    public void configure() throws Exception {
        if(groupId == null)
            groupId = "dl4j-serving";
        if(zooKeeperHost == null)
            zooKeeperHost = "localhost";
        String kafkaUri = String.format("kafka:%s?topic=%s&groupId=%s&zookeeperHost=%s&zookeeperPort=%d&serializerClass=%s&keySerializerClass=%s",
                kafkaBroker,
                consumingTopic
                ,groupId
                ,zooKeeperHost
                ,zooKeeperPort,
                StringEncoder.class.getName(),
                StringEncoder.class.getName());
        from(kafkaUri)
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        byte[] o = (byte[]) exchange.getIn().getBody();
                        byte[] arr  = Base64.decodeBase64(new String(o));
                        ByteArrayInputStream bis = new ByteArrayInputStream(arr);
                        DataInputStream dis = new DataInputStream(bis);
                        INDArray predict = Nd4j.read(dis);
                        if(computationGraph) {
                            ComputationGraph graph = ModelSerializer.restoreComputationGraph(modelUri);
                            INDArray[] output = graph.output(predict);
                            exchange.getOut().setBody(output);
                            exchange.getIn().setBody(output);

                        }
                        else {
                            MultiLayerNetwork network = ModelSerializer.restoreMultiLayerNetwork(modelUri);
                            INDArray output = network.output(predict);
                            exchange.getOut().setBody(output);
                            exchange.getIn().setBody(output);
                        }


                    }
                })
                .process(finalProcessor)
                .to(outputUri);
    }
}
