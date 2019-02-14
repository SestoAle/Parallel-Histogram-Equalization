/*
 ============================================================================
 Name        : Equalization_cuda.cu
 Author      : 
 Version     :
 Copyright   : Your copyright notice
 Description : CUDA compute reciprocals
 ============================================================================
 */

#include <iostream>
#include <numeric>
#include <stdlib.h>
#include <stdio.h>
#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include "Timer.h"

using namespace cv;
using namespace std;

int width;
int height;
bool with_gpu = true;
string file_name = "dark";

static int eq_CPU(unsigned char *input_ptr);
static int eq_GPU(unsigned char *input_ptr);
#define CUDA_CHECK_RETURN(value) CheckCudaErrorAux(__FILE__,__LINE__, #value, value)


//Kernel for the color conversion RGB to YCbCr and to compute the histogram of the Y channel.
__global__ void RGB_to_YCbCr_kernel(unsigned char *input, int* hist, int width, int height){

	//Create and initialize a shared histogram to decrease the latency caused by the access to the global memory.
	__shared__ int hist_priv[256];

	int idx = blockIdx.x*blockDim.x + threadIdx.x;

	long point_index;

	//for(int bin_idx = (threadIdx.x*blockDim.x + threadIdx.y); bin_idx < 256; bin_idx += blockDim.x*blockDim.y){
	for(int bin_idx = threadIdx.x; bin_idx < 256; bin_idx += blockDim.x){
		hist_priv[bin_idx] = 0;
	}

	__syncthreads();

	//If doesn't have the required number of threads, the access to the image in global memory is coalesced.
	//The image is saved in a buffer in order to ease the coalesced access;
	for(int i = idx; i < width*height; i += blockDim.x*gridDim.x){
		point_index = i*3;
		int r = input[point_index+0];
		int g = input[point_index+1];
		int b = input[point_index+2];

		int Y = (int) (0.299*r + 0.587*g + 0.114*b);
		int Cb = (int) (128 - 0.168736*r - 0.331264*g +0.5*b);
		int Cr = (int) (128 + 0.5*r - 0.418688*g - 0.081312*b);

		input[point_index+0] = Y;
		input[point_index+1] = Cb;
		input[point_index+2] = Cr;

		//Update the shared histogram.
		atomicAdd(&(hist_priv[Y]), 1);
	}
	__syncthreads();

	//The shared histograms are added to the global histogram.
	for(int bin_idx = threadIdx.x; bin_idx < 256; bin_idx += blockDim.x){
		atomicAdd(&(hist[bin_idx]), hist_priv[bin_idx]);
	}
}

//This kernel equalizes the histogram
__global__ void equalize_kernel(int* cdf, int* hist, int width, int height){

	int idx = blockIdx.x*blockDim.x + threadIdx.x;

	for(int i = idx; i < 256; i += blockDim.x*gridDim.x){
		hist[i] = (int) (((((float)cdf[i] - cdf[0]))/(((float)width*height - 1)))*255);
	}
}

//This kernel maps the new equalized values of the Y channel and
// makes the color conversion from YCbCr to RGB.
__global__ void YCbCr_to_RGB_kernel(unsigned char *input, int* hist, int* cdf, int width, int height){

	int idx = blockIdx.x*blockDim.x + threadIdx.x;

	//long index = (row*width + col);
	long point_index;

	for(int i = idx; i < width*height; i += blockDim.x*gridDim.x){

		point_index = i*3;

		int value_before = input[point_index];
		int value_after = hist[value_before];

		//input[point_index] = value_after;

		int y = value_after;
		int cb = input[point_index+1];
		int cr = input[point_index+2];

		int R = max(0, min(255, (int) (y + 1.402*(cr-128))));
		int G = max(0, min(255, (int) (y - 0.344136*(cb-128) - 0.714136*(cr-128))));
		int B = max(0, min(255, (int) (y + 1.772*(cb- 128))));

		input[point_index+0] = R;
		input[point_index+1] = G;
		input[point_index+2] = B;

	}
}

int main(void)
{
	//Load the image
	cout << "Loading image.." << endl;
	string input_name = "image/" + file_name + ".jpg";
	Mat input = imread(input_name, CV_LOAD_IMAGE_COLOR);

	if(!input.data){
		cout << "Image not found!" << endl;
		return -1;
	}

	height = input.rows;
	width = input.cols;

	//Convert the image into a buffer
	unsigned char *input_ptr = input.ptr();

	cout << "Starting to process.." << endl;

	//Start GPU timer
	GpuTimer timer;
	timer.Start();

	if(with_gpu){
		cout << "Processing with GPU" << endl;
		eq_GPU(input_ptr);
	}else{
		cout << "Processing with CPU" << endl;
		eq_CPU(input_ptr);
	}

	//Stop the GPU timer and show the elapsed time.
	timer.Stop();
	printf("Image equalized in %f msec!\n", timer.Elapsed());

	//Save the image
	cout << "Saving output image.."<< endl;
	string output_name;
	if(with_gpu){
		output_name = "image/" + file_name + "_equalized_gpu.jpg";
	} else{
		output_name = "image/" + file_name + "_equalized_cpu.jpg";
	}
	imwrite(output_name, input);
	cout << "Image saved!"<< endl;

	return 0;
}


//Check the return value of the CUDA runtime API call and exit the application if the call has failed.

static void CheckCudaErrorAux (const char *file, unsigned line, const char *statement, cudaError_t err)
{
	if (err == cudaSuccess)
		return;
	std::cerr << statement<<" returned " << cudaGetErrorString(err) << "("<<err<< ") at "<<file<<":"<<line << std::endl;
	exit (1);
}

static int eq_GPU(unsigned char *input_ptr){

	unsigned char *gpu_input;
	int *d_hist;
	int *d_cdf;
	int *d_hist_eq;
	int h_hist[256] = {0};

	//Allocate the GPU global memory needed.
	CUDA_CHECK_RETURN(cudaMalloc((void **)&gpu_input, sizeof(char)*(width*height*3)));
	CUDA_CHECK_RETURN(cudaMalloc((void **)&d_hist, sizeof(int)*(256)));
	CUDA_CHECK_RETURN(cudaMalloc((void **)&d_hist_eq, sizeof(int)*(256)));
	CUDA_CHECK_RETURN(cudaMalloc((void **)&d_cdf, sizeof(int)*(256)));

	//Copy the image buffer to the global memory.
	CUDA_CHECK_RETURN(cudaMemcpy(gpu_input, input_ptr, sizeof(char)*(width*height*3), cudaMemcpyHostToDevice));
	CUDA_CHECK_RETURN(cudaMemcpy(d_hist, h_hist, sizeof(int)*(256), cudaMemcpyHostToDevice));

	int block_size = 256;
	int grid_size = (width*height + (block_size-1))/block_size;

	//Call the first kernel.
	RGB_to_YCbCr_kernel<<<grid_size, block_size>>> (gpu_input, d_hist, width, height);

	//Copy to host the histogram computed in the first kernel.
	CUDA_CHECK_RETURN(cudaMemcpy(h_hist, d_hist, sizeof(int)*(256), cudaMemcpyDeviceToHost));

	int sum = 0;
	int h_cdf[256] = {0};

	for(int i = 0; i < 256; i++){
		sum += h_hist[i];
		h_cdf[i] = sum;
	}

	CUDA_CHECK_RETURN(cudaMemcpy(d_cdf, h_cdf, sizeof(int)*(256), cudaMemcpyHostToDevice));

	//Call the second kernel.
	equalize_kernel<<<grid_size, block_size>>> (d_cdf, d_hist_eq, width, height);

	//Call the third kernel.
	YCbCr_to_RGB_kernel<<<grid_size, block_size>>> (gpu_input, d_hist_eq, d_cdf, width, height);

	//Copy to host the equalized image.
	CUDA_CHECK_RETURN(cudaMemcpy(input_ptr, gpu_input, sizeof(char)*(width*height*3), cudaMemcpyDeviceToHost));

	//Release GPU memory.
	CUDA_CHECK_RETURN(cudaFree(gpu_input));
	CUDA_CHECK_RETURN(cudaFree(d_hist));
	CUDA_CHECK_RETURN(cudaFree(d_hist_eq));
	CUDA_CHECK_RETURN(cudaFree(d_cdf));

	return 0;
}

//Histogram Equalization with CPU
static int eq_CPU(unsigned char *input_ptr){

	int histogram[256] = {0};

	for (int i = 0; i< height*width*3; i+=3){
		int r = input_ptr[i+0];
		int g = input_ptr[i+1];
		int b = input_ptr[i+2];

		int Y = (int) (0.299*r + 0.587*g + 0.114*b);
		int Cb = (int) (128 - 0.168736*r - 0.331264*g +0.5*b);
		int Cr = (int) (128 + 0.5*r - 0.418688*g - 0.081312*b);

		input_ptr[i+0] = Y;
		input_ptr[i+1] = Cb;
		input_ptr[i+2] = Cr;

		histogram[Y] += 1;
	}

	int sum = 0;
	int histogram_equalized[256] = {0};

	for(int i = 0; i < 256; i++){
		sum += histogram[i];
		histogram_equalized[i] = (int) (((((float)sum - histogram[0]))/(((float)width*height - 1)))*255);

	}

	for (int i = 0; i< height*width*3; i+=3){
		int value_before = input_ptr[i];
		int value_after = histogram_equalized[value_before];

		input_ptr[i] = value_after;

		int y = input_ptr[i+0];
		int cb = input_ptr[i+1];
		int cr = input_ptr[i+2];

		int R = max(0, min(255, (int) (y + 1.402*(cr-128))));
		int G = max(0, min(255, (int) (y - 0.344136*(cb-128) - 0.714136*(cr-128))));
		int B = max(0, min(255, (int) (y + 1.772*(cb- 128))));

		input_ptr[i+0] = R;
		input_ptr[i+1] = G;
		input_ptr[i+2] = B;
	}

	return 0;
}

