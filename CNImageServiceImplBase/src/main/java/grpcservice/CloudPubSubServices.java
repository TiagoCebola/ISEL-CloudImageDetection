package grpcservice;

import cnimageservice.ReqId;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
public class CloudPubSubServices {

    static String PROJECT_ID = "cn2122-t1-g03";
    public TopicName pubTopicName;

    public CloudPubSubServices(TopicName topicName) {
        this.pubTopicName = topicName;
    }

    public void publishMessage(ReqId reqId, String bucketName, String blobName) throws Exception {

        //Cria o tópico onde irá publicar a mensagem
        TopicName topic = TopicName.ofProjectTopicName(PROJECT_ID, pubTopicName.getTopic());

        //Cria o "publicador" da mensagem associado ao respetivo topico
        Publisher publisher = Publisher.newBuilder(topic).build();

        //Cria uma String de bytes com o ID da respetiva imagem
        ByteString msgData = ByteString.copyFromUtf8(reqId.getId());

        //Cria a mensagem a ser publicada com a string anterior no corpo da mensagem e a informação do blob e do bucket como atributos
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(msgData)
                .putAttributes("bucket", bucketName)
                .putAttributes("blob", blobName)
                .build();

        //Publica a mensagem
        ApiFuture<String> future = publisher.publish(pubsubMessage);
        String msgID = future.get();
        System.out.println("Message Published with ID =" + msgID);

        //Termina o "publicador"
        publisher.shutdown();
    }


}
