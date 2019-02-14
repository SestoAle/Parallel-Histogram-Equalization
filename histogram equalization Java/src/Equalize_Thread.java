import java.util.concurrent.Callable;

public class Equalize_Thread implements Runnable{

    int id;
    int total_number;
    int width;
    int height;
    int[] histogram;
    int[] histogram_equalized;
    int[] cdf;

    Equalize_Thread(int id, int total_number, int[] histogram, int[] cdf, int[] histogram_equalized, int width, int height){

        this.id = id;
        this.total_number = total_number;
        this.width = width;
        this.height = height;

        this.histogram = histogram;
        this.cdf = cdf;
        this.histogram_equalized = histogram_equalized;
    }

    public void run(){

        for(int i = id*256/total_number; i < (id*256/total_number + 256/total_number); i++){

            histogram_equalized[i] = (int)((((float)(cdf[i] - cdf[0]))/((float)(width*height - 1))) * 255);

        }
    }

}
