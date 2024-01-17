package imageclient;

import cnimageservice.Void;
import com.google.gson.*;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import cnimageservice.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientApp {

    private static String svcIP = "34.175.250.72"; // "34.67.52.118";
    private static int svcPort = 8000;
    private static ManagedChannel channel;
    private static CNImageServiceGrpc.CNImageServiceBlockingStub  blockingStub;
    private static CNImageServiceGrpc.CNImageServiceStub  noBlockingStub;
    private static final Scanner scanner = new Scanner(System.in);
    private static final int CHUNK_MAX_SIZE_BYTES = 1_000_000;
    private static String defaultCloudFunction = "https://europe-west1-cn2122-t1-g03.cloudfunctions.net/Iplookup";

    static int Menu() {
        Scanner scan = new Scanner(System.in);
        int option;
        do {
            System.out.println("######## CN2122TF MENU ##########");
            System.out.println(" 0: Submit an image to detect objects");
            System.out.println(" 1: Get the detection result");
            System.out.println(" 2: Get all the files with an object");
            System.out.println("..........");
            System.out.println("99: Exit");
            System.out.print("Enter an Option: ");
            option = scan.nextInt();
        } while (!((option >= 0 && option <= 2) || option == 99));
        return option;
    }

    public static void requestImageDetection() {

        // Cria um ClientObserver que será enviado ao servidor para receber a resposta
        ClientRequestStreamObserver respObserver = new ClientRequestStreamObserver();

        // Chamada à função do servidor que retorna um observer onde será enviada a imagem
        StreamObserver<Img> imgObserver = noBlockingStub.submitImageDetectionObjectRequest(respObserver);

        // Pede o PATH da imagem a ser enviada
        System.out.print("Please insert the filepath of the image that you want to detect the objects: ");
        String imageName = scanner.nextLine();

        // Caso não seja passado nenhum nome lança exceção
        if(imageName.isEmpty()) {
            Status.INVALID_ARGUMENT.withDescription("Image name can't be empty").asException();
            System.out.println("Image name can't be empty");
        }

        // Obtém o ficheiro da imagem e o seu respetivo tamanho
        File imageFile = new File(imageName);
        long size = imageFile.length();

        // Criação da variável do contrato onde irá a informação dos metadados da imagem
        Img.Metadata metadata = Img.Metadata.newBuilder().setName(imageName).setSize(size).build();
        Img img = Img.newBuilder().setMetadata(metadata).build();

        // Envia os metadados da imagem para o servidor
        imgObserver.onNext(img);

        // Lê os bytes do ficheiro
        try(FileInputStream imgReader = new FileInputStream(imageFile)) {
            int numBytesRead;
            byte[] chunck = new byte[CHUNK_MAX_SIZE_BYTES];

            // Lê um X números de bytes da imagem, cria a variável do contrato com os bocados (chunks) da imagem e envia para o servidor
            while( (numBytesRead = imgReader.read(chunck)) != -1 ) {
                Img imageChunk = Img.newBuilder().setChunk(ByteString.copyFrom(ByteBuffer.wrap(chunck), numBytesRead)).build();
                imgObserver.onNext(imageChunk); // MANDA AO SERVER OS VARIOS CHUNCKS
            }
        }
        catch(Exception ex) {
            imgObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asException());
            System.out.println(ex.getMessage());
        }

        // Término do envio da imagem
        imgObserver.onCompleted();
    }

    public static void responseImageDetection() {

        // Pede o ID da imagem que foi submetida anteriormente
        System.out.print("Please insert the request id: ");
        String reqId = scanner.nextLine();

        // Caso não seja passado nenhum ID lança exceção
        if(reqId.isEmpty()) {
            Status.INVALID_ARGUMENT.withDescription("Request id can't be empty").asException();
            System.out.println("Request id can't be empty");
        }

        else {
            // Cria a variável do contrato
            ReqId requestId = ReqId.newBuilder().setId(reqId).build();

            // Chamada da função do servidor
            ImgWithObjDetected imgObj = blockingStub.requestImageDetectionObjectResult(requestId);

            // Printa as informações obtidas da chamada à função
            System.out.println("\nYou can get your new image from: " + imgObj.getDetectedImage()); // URL onde pode ser visualizada a imagem
            System.out.println("In your image it was detected the following objects: ");

            AtomicInteger idx = new AtomicInteger();
            imgObj.getObjectNamesList().forEach( objName -> {
                System.out.println("-> " + objName + " (" + imgObj.getAssuranceDegrees(idx.get()) + " assurance)"); // Objetos detetados na imagem
                idx.getAndIncrement();
            });
            System.out.println();
        }

    }

    public static String getAndFormatDate(SimpleDateFormat format) {
        // Função que verifica se a data está a ser escrita no formato pretendido

        String dt = "";
        boolean checkFormat = false;

        while(!checkFormat) {
            try {
                dt = scanner.nextLine();
                format.parse(dt);
                checkFormat = true;
            }
            catch(ParseException e) {
                System.out.println("Wrong date format, please Try Again!");
            }
        }

        return dt;
    }

    public static void fileResponseImage() {

        // Criação do formato pretendido para a data
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");

        // Pede a data inicial
        System.out.println("Insert the initial date (DD/MM/YYYY): ");
        //Date dt1 = getAndFormatDate(format);
        String dt1 = getAndFormatDate(format);

        // Pede a data final
        System.out.println("Insert the end date (DD/MM/YYYY): ");
        //Date dt2 = getAndFormatDate(format);
        String dt2 = getAndFormatDate(format);

        // Pede o objeto que pretende que a imagem contenha
        System.out.println("Insert the object name you want to search in the image: ");
        String objName = scanner.nextLine();

        // Pede o grau de certeza da imagem
        System.out.println("Insert the assurance degree (values between 0-1): ");
        double assuranceDegree = scanner.nextDouble();

        // Verifica se todos os valores obtidos existem, cria a variável do contrato e realiza a chamada à função do servidor seguido do print das respostas obtidas
        if( dt1 != null && dt2 != null && !objName.isEmpty() && !Double.isNaN(assuranceDegree) ) {
            FileReq fileRequest = FileReq.newBuilder().setDate1(dt1).setDate2(dt2).setObjectName(objName).setAssuranceDegree(assuranceDegree).build();
            FileResp allFiles = blockingStub.requestFileNamesResult(fileRequest);
            if(allFiles.getFileNameList().size() != 0) {
                System.out.println("\nThe following files correspond to the requirements");
                allFiles.getFileNameList().forEach( fileName -> System.out.println("-> " + fileName) );
            }
            else System.out.println("No file correspond to the requirements!");
            System.out.println();
        }
        else {
            System.out.println("The arguments are not correct");
        }

    }

    // TODO - Rever se retorna valor dif
    public static int getRandomInt(int minValue, int maxValue) {
        // Função que obtém um valor aleatório de acordo com os valores passados como parâmetro

        Random ran = new Random();
        return ran.nextInt(maxValue - minValue + 1) + minValue;
    }

    public static String getIpsFromVMs() {

        // Cria um cliente HTTP
        HttpClient client = HttpClient.newBuilder().build();

        // Cria um request HTTP com o url da respetiva Cloud Function
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(defaultCloudFunction))
                .GET()
                .build();

        java.net.http.HttpResponse<String> response;

        try {
            // Realiza o pedido HTTP
            response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            // Caso o pedido tenha sucesso
            if(response.statusCode() == 200) {

                Gson gson = new GsonBuilder().setLenient().create();
                List<JsonElement> elems = new ArrayList<>(Collections.emptyList());

                // Obtém todos os elementos do corpo da resposta, nomeadamente os IPs das VMs disponiveis
                String[] vmsIps  = response.body().split("\n");
                List<String> li = Arrays.asList(vmsIps);
                JsonArray jArray = gson.fromJson(li.toString(), JsonArray.class);
                if (jArray != null) {
                    for (int i = 0 ; i<jArray.size() ; i++){
                        elems.add(jArray.get(i));
                    }
                }

                // Obtém um valor aleatório entre 1 e o número de VMs disponíveis, decrementado de 1 de forma a coincidir com o respetivo indice da lista
                int randomIp = getRandomInt(1, elems.size()) - 1;

                // Retorna o endereço IP referente à VM no indice da lista coincidente com o valor obtido anteriormente
                return elems.get(randomIp).getAsJsonObject().get("IpAddress").getAsString();
            }
            else if(response.statusCode() == 404 ) return "Resource Not Found"; // Caso o pedido não seja encontrado
            else if(response.statusCode() == 500 ) return "Internal Server Error"; // Caso exista algum erro do lado da implementação
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return "Error";
    }

    public static void main(String[] args) {

        try {

            String vmIp = "Errpr";
            while( vmIp == "Error") {
                vmIp = getIpsFromVMs();
            };
            System.out.println("You are connected to the VM with IP = " + vmIp);

            //TODO -> svcIP = vmIp;
            channel = ManagedChannelBuilder.forAddress(svcIP, svcPort)
                    // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                    // needing certificates.
                    .usePlaintext()
                    .build();

            blockingStub =  CNImageServiceGrpc.newBlockingStub(channel);
            noBlockingStub = CNImageServiceGrpc.newStub(channel);

            // Trying to connect to the server
            long startTime = System.currentTimeMillis();
            Void req = Void.newBuilder().build();
            Text text = blockingStub.isAlive(req);
            System.out.println(text.getMsg());
            long endTime = System.currentTimeMillis();
            System.out.println("Ping Operation completed in: " + (endTime-startTime) + " ms");


            boolean end = false;
            while (!end) {

                try {
                    int option = Menu();
                    switch (option) {
                        case 0:
                            requestImageDetection();
                            break;

                        case 1:
                            responseImageDetection();
                            break;

                        case 2:
                            fileResponseImage();
                            break;

                        case 99:
                            end = true;
                            System.exit(0);
                    }

                } catch (Exception ex) {
                    System.out.println("\nERROR executing operations!\n");
                    ex.printStackTrace();
                }

            }

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
