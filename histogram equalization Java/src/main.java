import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;

import static java.lang.Math.round;

public class main {

    static int resize_scale = 8;
    static String file_name = "dark";

    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return dimg;
    }

    public static void RGB_to_YBR(BufferedImage image, int[] histogram){

        Raster raster = image.getRaster();

        for (int y = 0; y < image.getHeight(); y++) {
            int[] iarray = new int[image.getWidth()*3];

            raster.getPixels(0, y, image.getWidth(), 1, iarray);

            for (int x = 0; x < image.getWidth()*3; x+=3) {

                int r = iarray[x+0];
                int g = iarray[x+1];
                int b = iarray[x+2];

                int Y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int Cb = (int) (128 - 0.168736 * r - 0.331264 * g + 0.5 * b);
                int Cr = (int) (128 + 0.5 * r - 0.418688 * g - 0.081312 * b);


                iarray[x+0] = Y;
                iarray[x+1] = Cb;
                iarray[x+2] = Cr;

                histogram[Y] ++;
            }

            image.getRaster().setPixels(0, y, image.getWidth(), 1, iarray);
        }
    }

    public static void YBR_to_RGB(BufferedImage image, int[] histogram_equalized){

        Raster raster = image.getRaster();

        for (int y = 0; y < image.getHeight(); y ++) {
            int[] iarray = new int[image.getWidth()*3];

            raster.getPixels(0, y, image.getWidth(), 1, iarray);

            for (int x = 0; x < image.getWidth()*3; x+=3) {

                int valueBefore = iarray[x];
                int valueAfter = histogram_equalized[valueBefore];

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
            image.getRaster().setPixels(0,y, image.getWidth(), 1,  iarray);
        }
    }


    public static void main(String[] args){


        BufferedImage img = null;
        try {

            //Load the image
            System.out.println("Loading Image..");
            img = ImageIO.read(new File("image/" + file_name + ".jpg"));
            //img = resize(img, img.getWidth()*2, img.getHeight()*2);

            //Display the image
            ImageIcon icon = new ImageIcon(resize(img, img.getWidth()/resize_scale, img.getHeight()/resize_scale));
            JLabel label = new JLabel(icon, JLabel.CENTER);
            JOptionPane.showMessageDialog(null, label, "Image not Equalized", -1);

            System.out.println("Start processing..");
            long startTime = System.currentTimeMillis();

            int[] histogram = new int[256];

            //Convert the image form RGB to YCbCr
            RGB_to_YBR(img, histogram);

            long time_1 = System.currentTimeMillis();
            System.out.println("First op done in: " + (time_1 - startTime) + " msec");

            int width = img.getWidth();
            int height = img.getHeight();
            int[] histogram_equalized = new int[256];

            int sum = 0;

            //Equalized the histogram
            for(int i = 0; i < histogram.length; i++){

                sum += histogram[i];
                histogram_equalized[i] = (int)((((float)(sum - histogram[0]))/((float)(width*height - 1))) * 255);

            }

            long time_2 = System.currentTimeMillis();
            System.out.println("Second op done in: " + (time_2 - time_1) + " msec");

            //Map the new value of the Y channel and convert the image from YCbCr to RGB
            YBR_to_RGB(img, histogram_equalized);

            long endTime   = System.currentTimeMillis();
            System.out.println("Third op done in: " + (endTime - time_2) + " msec");
            long totalTime = endTime - startTime;
            System.out.println("Total time: " + totalTime);

            System.out.println("Showing the equalized image..");
            //Display the image
            icon = new ImageIcon(resize(img, img.getWidth()/resize_scale, img.getHeight()/resize_scale));
            label = new JLabel(icon, JLabel.CENTER);
            JOptionPane.showMessageDialog(null, label, "Image Equalized", -1);

            System.out.println("Saving the image..");
            //Save output image
            //File output_file = new File("image/" + file_name + "_equalized_seq.jpg");
            //ImageIO.write(img, "jpg", output_file);
            //System.out.println("Image saved!");
        } catch (IOException e){
            System.out.println(e);
        }

    }
}
