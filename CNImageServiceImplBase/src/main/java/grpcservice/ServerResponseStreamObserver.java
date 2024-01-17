package grpcservice;


import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.storage.BlobInfo;
import cnimageservice.Img;
import cnimageservice.ReqId;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.TopicName;
import grpcservice.Firestore.CloudFirestoreServices;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;

public class ServerResponseStreamObserver implements StreamObserver<Img> {

    private final StreamObserver<ReqId> responseObserver;
    private final CloudFirestoreServices fsServices;
    private final CloudStorageServices storageServices;
    private CloudStorageServices.ChunkingServices chunkingServices;
    private CloudPubSubServices pubSubServices;
    private ReqId reqId;
    private Img.Metadata metadata;
    private BlobInfo blobInfo;
    private String bucketName;
    private String cloudTopicName = "detectionworkers";
    private long readBytes = 0L;

    public ServerResponseStreamObserver(StreamObserver<ReqId> responseObserver, CloudFirestoreServices fsServices,
                                        CloudStorageServices storageServices, String defaultBucketName)
    {
        this.responseObserver = responseObserver;
        this.fsServices = fsServices;
        this.storageServices = storageServices;
        this.bucketName = defaultBucketName;
    }

    @Override
    public void onNext(Img img) {
        try {

            // Inicialmente são enviados os metadados da imagem, aqui é realizada a verificação se é a primeira mensagem
            if(img.hasMetadata()) {
                metadata = img.getMetadata();

                // Obtém o tipo da imagem recebida
                String imageName = metadata.getName();
                String imageType = "." + imageName.split("\\.")[1];

                // Gera o documento na Firestore e consequentemente o respetivo ID com que a imagem será guardada
                String respId = fsServices.generateCollectionDoc(bucketName, imageType);

                // Cria o Blob da respetiva imagem
                blobInfo = BlobInfo
                        .newBuilder(bucketName, respId + imageType)
                        .setContentType("image/jpeg")
                        .build();

                // Cria o Topic onde será publicada a mensagem com a respetiva imagem
                TopicName topicName = TopicName.newBuilder()
                        .setProject(storageServices.getProjectId())
                        .setTopic(cloudTopicName)
                        .build();

                // Inicializa o serviço Pub/Sub com o topico criado
                pubSubServices = new CloudPubSubServices(topicName);

                // Cria o reqID que será posteriormente enviado como resposta ao cliente
                reqId = ReqId.newBuilder().setId(respId).build();
            }
            else {
                // Caso não seja a primeira mensagem, são enviados bocados da imagem (chunks)
                ByteString chunk = img.getChunk();

                // Caso o tamanho da imagem seja inferior ao valor referido, é possível guardar diretamente a imagem no cloud Storage
                if (metadata.getSize() <= 1_000_000) {
                    storageServices.storeImage(blobInfo, chunk);
                }
                else {
                    // Caso contrário terá de ser adicionado aos poucos

                    // Quando ainda não foi lido nenhum chunk, é criado o serviço responsável por guarda-los para posterior submissão no cloud storage
                    if (readBytes == 0L) {
                        chunkingServices = storageServices.getChunkingServices(blobInfo);
                    }

                    // Guarda o chunk da imagem no canal e incrementa a quantidade de Bytes que já foram lidos
                    chunkingServices.storeImageChunk(chunk);
                    readBytes += chunk.size();

                    // Caso o tamanho de bytes lidos corresponda ao tamanho total da imagem o canal é fechado.
                    if (readBytes == metadata.getSize()) {
                        chunkingServices.closeChannel();
                    }

                }
            }
        } catch (Exception ex) {
            System.out.println(ex.getStackTrace());
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asException());
        }
    }

    @Override
    public void onError(Throwable throwable) {
        StatusException ex = Status.fromThrowable(throwable).asException();
        System.out.println(ex.getMessage());
    }

    @Override
    public void onCompleted() {
        try {
            //Publica a imagem no serviço Pub/Sub
            pubSubServices.publishMessage(reqId, bucketName, blobInfo.getName());

            responseObserver.onNext(reqId); //Envia o ID para o cliente
            responseObserver.onCompleted(); //Termina a função
        }
        catch (Exception ex) {
            System.out.println(ex.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asException());
        }
    }
}
