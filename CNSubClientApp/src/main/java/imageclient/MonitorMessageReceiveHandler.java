package imageclient;

import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.storage.*;
import com.google.cloud.vision.v1.*;
import com.google.cloud.vision.v1.Image;
import com.google.pubsub.v1.PubsubMessage;
import imageclient.Firestore.CloudFirestoreServices;
import imageclient.Firestore.ObjectInfo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MonitorMessageReceiveHandler implements MessageReceiver  {


    @Override
    public void receiveMessage(PubsubMessage pubsubMessage, AckReplyConsumer ackReplyConsumer) {

        try {

            // Obtém os atributos da mensagem, nomeadamente o bucket e o blob da imagem original
            Map<String, String> atribs = pubsubMessage.getAttributesMap();
            String bucketName = atribs.get("bucket");
            String blobName = atribs.get("blob");

            // Inicializa o serviço de Cloud Storage
            StorageOptions storageOptions = StorageOptions.getDefaultInstance();
            Storage storage = storageOptions.getService();

            // Inicializa o serviço de Firestore
            CloudFirestoreServices fsServices = new CloudFirestoreServices();

            // Deteta os objetos na imagem, anotando-os na mesma e guarda a imagem anotada no respetivo bucket da cloud storage
            List<ObjectInfo> objInfoList = detectLocalizedObjectsGcs(storage, bucketName, blobName);

            // Coloca os objetos detetados e os seus respetivos graus de certeza no documento do firestore
            fsServices.insertMessage(objInfoList, blobName, pubsubMessage.getData().toStringUtf8());

            // Acknowledge da mensagem para não voltar a ser consumida por outro worker
            ackReplyConsumer.ack();


        } catch (Exception ex) {
            ackReplyConsumer.nack();
            ex.printStackTrace();
        }

    }


    //TODO - NECESSÁRIO COMENTAR ESTAS FUNÇÕES?
    public static List<ObjectInfo> detectLocalizedObjectsGcs(Storage storage, String bucketName, String blobName) throws IOException {

        String gcsPath = "gs://" + bucketName + "/" + blobName;
        ImageSource imgSource = ImageSource.newBuilder().setGcsImageUri(gcsPath).build();
        Image img = Image.newBuilder().setSource(imgSource).build();

        AnnotateImageRequest request =
                AnnotateImageRequest.newBuilder()
                        .addFeatures(Feature.newBuilder().setType(Feature.Type.OBJECT_LOCALIZATION))
                        .setImage(img)
                        .build();

        BatchAnnotateImagesRequest singleBatchRequest = BatchAnnotateImagesRequest.newBuilder()
                .addRequests(request)
                .build();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {

            // SAVES THE OBJS FOUND IN THE VISION API
            List<ObjectInfo> objInfoList = new ArrayList<>(Collections.emptyList());


            // Perform the request
            BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(singleBatchRequest);
            List<AnnotateImageResponse> listResponses = batchResponse.getResponsesList();

            if (listResponses.isEmpty()) {
                System.out.println("Empty response, no object detected.");
                return objInfoList;
            }
            // get the only response
            AnnotateImageResponse response = listResponses.get(0);
            // print information in standard output
            for (LocalizedObjectAnnotation annotation : response.getLocalizedObjectAnnotationsList()) {

                // OBJ INFO FROM VISION API
                ObjectInfo info = new ObjectInfo();
                info.name = annotation.getName();
                info.assuranceDegree = annotation.getScore();

                objInfoList.add(info);

                System.out.format("Object name: %s%n", annotation.getName());
                System.out.format("Confidence: %s%n", annotation.getScore());
                System.out.format("Normalized Vertices:%n");
                annotation
                        .getBoundingPoly()
                        .getNormalizedVerticesList()
                        .forEach(vertex -> System.out.println("(" + vertex.getX() + ", " + vertex.getY() + ")"));
            }

            // annotate in memory Blob image
            BufferedImage bufferImg = getBlobBufferedImage(storage, BlobId.of(bucketName, blobName));
            annotateWithObjects(bufferImg, response.getLocalizedObjectAnnotationsList());
            // save the image to a new blob in the same bucket. The name of new blob has the annotated prefix
            writeAnnotatedImage(storage, bufferImg, bucketName, "annotated-" + blobName);

            return objInfoList;
        }

    }

    private static void writeAnnotatedImage(Storage storage, BufferedImage bufferImg, String bucketName, String destinationBlobName) throws IOException {
        BlobInfo blobInfo = BlobInfo
                .newBuilder(BlobId.of(bucketName, destinationBlobName))
                .setContentType("image/jpeg")
                .build();
        Blob destBlob = storage.create(blobInfo);
        WriteChannel writeChannel = storage.writer(destBlob);
        OutputStream out = Channels.newOutputStream(writeChannel);
        ImageIO.write(bufferImg, "jpg", out);
        out.close();


        Blob blob = storage.get(blobInfo.getBlobId());
            Acl.Entity aclEnt = Acl.User.ofAllUsers();
            Acl.Role role = Acl.Role.READER;
            Acl acl = Acl.newBuilder(aclEnt, role).build();
        blob.createAcl(acl);
    }

    private static BufferedImage getBlobBufferedImage(Storage storage, BlobId blobId) throws IOException {
        Blob blob = storage.get(blobId);
        if (blob == null) {
            System.out.println("No such Blob exists !");
            throw new IOException("Blob <" + blobId.getName() + "> not found in bucket <" + blobId.getBucket() + ">");
        }
        ReadChannel reader = blob.reader();
        InputStream in = Channels.newInputStream(reader);
        return ImageIO.read(in);
    }

    public static void annotateWithObjects(BufferedImage img, List<LocalizedObjectAnnotation> objects) {
        for (LocalizedObjectAnnotation obj : objects) {
            annotateWithObject(img, obj);
        }
    }

    private static void annotateWithObject(BufferedImage img, LocalizedObjectAnnotation obj) {
        Graphics2D gfx = img.createGraphics();
        gfx.setFont(new Font("Arial", Font.PLAIN, 18));
        gfx.setStroke(new BasicStroke(3));
        gfx.setColor(new Color(0x00ff00));
        Polygon poly = new Polygon();
        BoundingPoly imgPoly = obj.getBoundingPoly();
        // draw object name
        gfx.drawString(obj.getName(),
                imgPoly.getNormalizedVertices(0).getX() * img.getWidth(),
                imgPoly.getNormalizedVertices(0).getY() * img.getHeight() - 3);
        // draw bounding box of object
        for (NormalizedVertex vertex : obj.getBoundingPoly().getNormalizedVerticesList()) {
            poly.addPoint((int) (img.getWidth() * vertex.getX()), (int) (img.getHeight() * vertex.getY()));
        }
        gfx.draw(poly);
    }

}
