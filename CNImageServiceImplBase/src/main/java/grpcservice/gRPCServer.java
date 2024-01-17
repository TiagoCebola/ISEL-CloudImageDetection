package grpcservice;

import cnimageservice.*;
import cnimageservice.Void;
import grpcservice.Firestore.CloudFirestoreServices;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.util.Scanner;

public class gRPCServer extends CNImageServiceGrpc.CNImageServiceImplBase {

    private static int svcPort = 8000;
    private final CloudFirestoreServices firestoreServices = new CloudFirestoreServices("objectImageDetector");
    private final CloudStorageServices storageServices = new CloudStorageServices("CN2122-T1-G03");

    @Override
    public void isAlive(Void request, StreamObserver<Text> responseObserver) {

        Text text = Text.newBuilder().setMsg("Server is alive").build(); //Cria a mensagem de "is alive"
        System.out.println("IsAlive called");

        try {
            Thread.sleep(5*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        responseObserver.onNext(text); //Envia a mensagem para o client
        responseObserver.onCompleted(); //Termina a função

    }

    @Override
    public StreamObserver<Img> submitImageDetectionObjectRequest(StreamObserver<ReqId> responseObserver) {
        //Retorna a stream para o client, onde irá receber os vários bocados da imagem para carregar na cloud storage
        return new ServerResponseStreamObserver(responseObserver, firestoreServices, storageServices, CloudStorageServices.DEFAULT_BUCKET_ID);
    }

    @Override
    public void requestImageDetectionObjectResult(ReqId request, StreamObserver<ImgWithObjDetected> responseObserver) {
        try {
            //Obtém a partir da Firestore a informação pretendida do documento com o ID recebido
            ImgWithObjDetected imgWithObjDetected = firestoreServices.readDocumentByID(request.getId());

            responseObserver.onNext(imgWithObjDetected); //Envia a informação para o client
            responseObserver.onCompleted(); //Termina a função
        }
        catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    @Override
    public void requestFileNamesResult(FileReq request, StreamObserver<FileResp> responseObserver) {
        try {
            //Obtém a partir da Firestore a os documentos que vão de encontro às restrições recebidas
            FileResp fileResp = firestoreServices.composeQueryWithIndex(request.getDate1(), request.getDate2(), request.getObjectName(), request.getAssuranceDegree());

            responseObserver.onNext(fileResp); //Envia a informação para o client
            responseObserver.onCompleted(); //Termina a função
        }
        catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0) svcPort = Integer.parseInt(args[0]);
            io.grpc.Server svc = ServerBuilder
                    .forPort(svcPort)
                    .addService(new gRPCServer())
                    .build();

            svc.start();

            System.out.println("Server started, listening on " + svcPort);
            Scanner scan= new Scanner(System.in);
            scan.nextLine();

            svc.shutdown();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
