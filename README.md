# Parallel-Histogram-Equalization
A simple parallel histogram equalization in CUDA and Java.

<p align="center">
<img  src="https://i.imgur.com/GgCD8i9.png" width="90%" height="90%"/>
</p>

The histogram equalization is a process to enhance an image based on its histogram, tipically used in greylevel images.
The same process of greylevel equalization can be applied to a RGB image:
* first we apply color space conversion from RGB to YCbCr;
* then we compute the histogram of the Y channel values;
* we equalize the Y histogram and map the old values to the new ones;
* finally we invert the color space from YCbCr to RGB.
This color space is suitable for us because the Y values are the luminance values, and in order to enhance the contrast of 
a figure we have to equalize them.

This operation can be done in **parallel** to increase the efficency and speed up the equalization.

## Implementation
The implementation covers both sequential and parallel version.

### Sequential version
For the implementation were used Java and C/CUDA. The sequential one is very similar for both languages:
* we load a image file and for each pixel we convert it from RGB to YCbCr while we compute the Y channel histogram;
* we make the histogram equalization;
* for each pixel of the image, we map the old values to the new ones and we make the conversion
from YCbCr to RGB.
In Java we use a BufferedImage object, while in C the image is treated like an array of char.

## Parallel version

#### Java
In java we use Java threads. The task is to parallelize the 3 main cycles, with 3 different type of threads.

The first type provides the color space conversion from RGB to YCbCr while computes locally the histogram. 
Each thread takes in input a non overlapping sub-part of the image; at the end, all the local histograms are added together to make the global histogram. The thread execution is managed by a Thread Pool.

The second type computes the equalized histogram. It takes the cdf, the histogram and it works 
only on a sub-part of the equalized histogram.

The third type is like the first, where each thread works on a sub-part of the image and it maps the new value 
based on the new histogram. It Converts from YCbCr to RGB.

#### CUDA
In CUDA we use 3 different kernels. For the memory management we allocate some objects with cudaMalloc in the global memory:
* a buffer for the input image, and then we do a cudaMemCpy to move it from host to the decive;
* an array of int of size 256 for the histogram;
* an array of int of size 256 for the cdf;
* an array of int of size 256 for the equalized histogram.

For the first type we create a ```__shared__ int histogram [256]``` to compute the histogram locally in a block;
Then the kernel converts the image from RGB to YCbCr. If the numebr of thread is greater or equal to the number of pixels,
then each thread will process only one pixel, else each thread will
process contiguous elements of different sections of the image.

<p align="center">
<img  src="https://i.imgur.com/PZRPc44.png" width="50%" height="50%"/>
</p>

Once the first kernel completes, the histogram is copied from device to host and the cdf is calculated in the CPU, 
then it’s copied from host to device. The second kernel equalizes the histogram, while the third maps the old values 
to the new ones and converts the image from YCbCr to RGB, exactly like the first one.

## Experiments and results

<p align="center">
<img  src="https://i.imgur.com/NjVO3GJ.png" width="100%" height="100%"/>
</p>

## Report
A copy of the report (italian) can be found
<a href="https://github.com/SestoAle/Parallel-Histogram-Equalization/raw/master/report/report.pdf" download="report.pdf">here</a>.

A copy of the presentation can be found
<a href="https://github.com/SestoAle/Parallel-Histogram-Equalization/raw/master/report/presentation.pdf" download="presentation.pdf">here</a>.

## License
Licensed under the term of [MIT License](https://github.com/SestoAle/Parallel-Histogram-Equalization/blob/master/LICENSE).
