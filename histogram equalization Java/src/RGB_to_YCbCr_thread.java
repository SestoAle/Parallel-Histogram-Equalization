import java.awt.image.*;
import java.util.concurrent.Callable;

public class RGB_to_YCbCr_thread implements Callable<int[]> {

    final private BufferedImage img;

    RGB_to_YCbCr_thread(BufferedImage sub_img){
        this.img = sub_img;
    }

    public int[] call(){
        return RGB_to_YBR(this.img);
    }

    public int[] RGB_to_YBR(BufferedImage sub_image) {

        int width = sub_image.getWidth();
        int height = sub_image.getHeight();
        int[] histogram = new int[256];
        int[] iarray = new int[width*3];

        for (int y = 0; y < height; y++) {

            sub_image.getRaster().getPixels(0, y, width, 1, iarray);

            for (int x = 0; x < width*3; x+=3) {

                int r = iarray[x+0];
                int g = iarray[x+1];
                int b = iarray[x+2];

                int Y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int Cb = (int) (128 - 0.168736 * r - 0.331264 * g + 0.5 * b);
                int Cr = (int) (128 + 0.5 * r - 0.418688 * g - 0.081312 * b);

                iarray[x+0] = Y;
                iarray[x+1] = Cb;
                iarray[x+2] = Cr;

                histogram[Y]++;
                
            }
            sub_image.getRaster().setPixels(0, y, width, 1, iarray);
        }

        return histogram;
    }
}
