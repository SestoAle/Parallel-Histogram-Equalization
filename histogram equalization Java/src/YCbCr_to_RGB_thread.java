import java.awt.image.BufferedImage;
import java.awt.image.Raster;

public class YCbCr_to_RGB_thread implements Runnable {

    int[] histogram;
    BufferedImage img;

    YCbCr_to_RGB_thread(BufferedImage img, int[] histogram){
        this.img = img;
        this.histogram = histogram;
    }

    public void run(){
        YBR_to_RGB(img);
    }

    public void YBR_to_RGB(BufferedImage sub_image){

        int width = sub_image.getWidth();
        int height = sub_image.getHeight();
        int[] iarray = new int[width*3];

        for (int y = 0; y < height; y++){

            sub_image.getRaster().getPixels(0, y, width, 1, iarray);

            for (int x = 0; x < width*3; x +=3) {

                int valueBefore = iarray[x];
                int valueAfter = histogram[valueBefore];

                iarray[x] = valueAfter;

                int Y = iarray[x+0];
                int cb = iarray[x+1];
                int cr = iarray[x+2];

                int R = Math.max(0, Math.min(255, (int) (Y + 1.402 * (cr - 128))));
                int G = Math.max(0, Math.min(255, (int) (Y - 0.344136 * (cb - 128) - 0.714136 * (cr - 128))));
                int B = Math.max(0, Math.min(255, (int) (Y + 1.772 * (cb - 128))));

                iarray[x+0] = R;
                iarray[x+1] = G;
                iarray[x+2] = B;
            }
            sub_image.getRaster().setPixels(0, y, width, 1, iarray);
        }
    }

}
