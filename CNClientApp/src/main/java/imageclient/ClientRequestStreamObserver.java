package imageclient;

import cnimageservice.ReqId;
import io.grpc.stub.StreamObserver;

public class ClientRequestStreamObserver implements StreamObserver<ReqId> {
    private boolean isCompleted = false;
    private boolean success = false;

    private ReqId reqId = null;
    private Throwable message = null;

    @Override
    public void onNext(ReqId reqId) {
        this.reqId = reqId;
    }

    @Override
    public void onError(Throwable throwable) {
        this.message = throwable;
        System.out.println("Error on call:"+ throwable.getMessage());
        isCompleted=true; success=false;
    }

    @Override
    public void onCompleted() {
        System.out.println("\nServer response: Request ID = " + reqId.getId());
        isCompleted=true; success=true;
    }


}
