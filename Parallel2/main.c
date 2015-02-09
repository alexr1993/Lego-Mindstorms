#include <stdio.h>
#include <stdlib.h>
#include <mpi.h>
#include <math.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#define LEN (128)

double PRECISION;
int DIM;
double* ARR1;
double* ARR2;
int PREC_FLAG = 0;

int MYRANK, NPROC;
MPI_Status STATUS;


double* createArray()
{
    double* values = (double*) malloc(DIM*DIM*sizeof(double));
    if ( values == NULL )
    {
        perror("Error: failed to create double*");
        exit(1);
    }
    return values;
}

void fillArray(double* arr)
{
    int i;
    for(i=0;i<(DIM*DIM);i++)
    {
        arr[i] = 0;
    }
    int size = (DIM*DIM)/2;
    for(i=size;i<(DIM*DIM);i++)
    {
        arr[i] = 1;
    }
    /*
    for(i=0;i<DIM;i++)
    {
        arr[i] = 1;
    }*/

}

void destroyArray(double* arr)
{
    free(arr);
}

void printArray(double* arr)
{
    int i;
    for(i=0;i<(DIM*DIM);i++)
    {
        printf("%f(%2d %2d)\t", arr[i], i, MYRANK);
        /* prints newline after row
        * for easier reading */
        if (i%DIM == (DIM - 1)) printf("\n");
    }
    printf("\n");
}

double* genArray()
{
    double* arr = createArray();
    fillArray(arr);
    return arr;
}

int calcStart(int rank)
{
    int start;
    int size = (DIM-2);
    div_t div_struct = div(size, NPROC);
    if (rank == 0) start = 0;
    else
    {
        if ( rank < div_struct.rem) start = rank*((div_struct.quot+1)*size);
        else start = (rank*div_struct.quot+div_struct.rem)*size;
    }
    return start;
}

int calcEnd(int rank)
{
    int end = calcStart(rank+1);
    return end;
}

/* some weird method to convert from inner array
 * index to outer array index */
int getOuterID(int innerID)
{
    div_t div_struct = div(innerID, (DIM-2));
    int outerID = (div_struct.quot+1)*DIM+1+(div_struct.rem);
    return outerID;
}

double calcAverage(double* arr, int i)
{
    double sum = (arr[i-1] + arr[i+1] + arr[i+DIM] + arr[i-DIM]);
    double average = sum / 4;
    return average;
}

void averageRange(int start, int end, double* oldArr, double* newArr)
{
    int i;
    int outerID;
    for (i = start; i < end; i++)
    {
        outerID = getOuterID(i);
        newArr[outerID] = calcAverage(oldArr, outerID);
        if ( PREC_FLAG == 0 )
        {
            outerID = getOuterID(i);
            double myDiff = fabs(oldArr[outerID] - newArr[outerID]);
            if (myDiff > PRECISION)
            {
                PREC_FLAG = 1;
            }
        }

    }
}


void swapArrays()
{
    double* temp = ARR1;
    ARR1 = ARR2;
    ARR2 = temp;
}

void setup()
{
    /* Gen and fill arrays */
    ARR1 = genArray();
    ARR2 = genArray();
}

/* Passes and recieves the relevant rows required
   to perform the next averaging iteration. */
void update_array(double* arr)
{
    int size = (DIM-2);
    int start = getOuterID(calcStart(MYRANK));
    int end = getOuterID(calcEnd(MYRANK)-size);
    int start_below = end+DIM;
    int end_above = start-DIM;
    /* First Row */
    if (MYRANK == 0)
    {
        /* Send my updated value to processor below */
        /* Receive updated value for row below */
        MPI_Sendrecv(&arr[end], size, MPI_DOUBLE, (MYRANK+1), 1,
                     &arr[start_below], size, MPI_DOUBLE,
                     (MYRANK+1), 1, MPI_COMM_WORLD, &STATUS);
    }
    /* Last Row */
    if (MYRANK == (NPROC-1))
    {
        /* Send my updated value to processor above */
        /* Receive updated value for row above */
        MPI_Sendrecv(&arr[start], size, MPI_DOUBLE, (MYRANK-1), 1,
                     &arr[end_above], size, MPI_DOUBLE,
                     (MYRANK-1), 1, MPI_COMM_WORLD, &STATUS);
    }
    /* Intermediate Rows */
    if ((MYRANK != 0)&&(MYRANK<(NPROC-1)))
    {
        /* Send my updated values to processor above and below */
        /* Receive updated values for row above and row below */
        MPI_Sendrecv(&arr[end], size, MPI_DOUBLE, (MYRANK+1), 1,
                     &arr[start_below], size, MPI_DOUBLE,
                     (MYRANK+1), 1, MPI_COMM_WORLD, &STATUS);
        MPI_Sendrecv(&arr[start], size, MPI_DOUBLE, (MYRANK-1), 1,
                     &arr[end_above], size, MPI_DOUBLE,
                     (MYRANK-1), 1, MPI_COMM_WORLD, &STATUS);
    }

}

/* Gather rows from each proc
   Pass to root */
void gather_rows(double* arr)
{
    int size = (DIM-2);
    int *counts;
    int *offsets;
    counts = malloc(NPROC * sizeof(int));
    offsets = malloc(NPROC * sizeof(int));
    int error,i;
    for (i=0;i<NPROC;i++)
    {
        int start = getOuterID(calcStart(i));
        offsets[i]=start-1;
        div_t div_struct = div(size, NPROC);
        /* size varies dependent on which proccess you are */
        int chunk;
        if (i<div_struct.rem) chunk = (div_struct.quot*DIM)+DIM;
        else chunk = div_struct.quot*DIM;
        counts[i]= chunk;
    }
    if (MYRANK < NPROC)
    {
        int start = offsets[MYRANK];
        error = MPI_Gatherv(&arr[start],counts[MYRANK],MPI_DOUBLE,
                            &arr[0],counts,offsets,MPI_DOUBLE,
                            0,MPI_COMM_WORLD);
        if(error) printf("Error on proc %d\n", MYRANK);
    }
}

void relax_array()
{
    if(MYRANK == 0)
    {
        /*
        printArray(ARR2);
        */
    }
    int iter;
    averageRange(calcStart(MYRANK),calcEnd(MYRANK),ARR1,ARR2);
    if (NPROC > 1) update_array(ARR2);
    MPI_Allreduce(&PREC_FLAG, &PREC_FLAG, 1, MPI_INT, MPI_SUM,MPI_COMM_WORLD);
    while (PREC_FLAG != 0)
    {
        PREC_FLAG = 0;
        swapArrays();
        averageRange(calcStart(MYRANK),calcEnd(MYRANK),ARR1,ARR2);
        if (NPROC > 1) update_array(ARR2);
        MPI_Allreduce(&PREC_FLAG, &PREC_FLAG, 1, MPI_INT, MPI_SUM, MPI_COMM_WORLD);
        iter++;
    }
    gather_rows(ARR2);
    if (MYRANK == 0 )
    {
        printf("Iter: %d Dim: %d Prec: %f Procs: %d\n", iter, DIM, PRECISION, NPROC);
        /*
        printArray(ARR2);
        */
    }
}

/* Setup arrays and then relax */
void start_processes()
{
    int rc;
    int size = (DIM-2);
    rc = MPI_Init(NULL, NULL);
    if (rc != MPI_SUCCESS)
    {
        printf ("Error starting MPI program\n");
        MPI_Abort(MPI_COMM_WORLD, rc);
    }
    MPI_Comm_rank(MPI_COMM_WORLD, &MYRANK);
    MPI_Comm_size(MPI_COMM_WORLD, &NPROC);
    if (MYRANK == 0)
    {
        if (NPROC > size)
        {
            /* Runs with proc per inner row so
               dimension-2 is the max */
            printf("Please run with %d or less procs\n", size);
            exit(0);
        }
        setup();
    }
    if ( (MYRANK != 0) && (MYRANK < size) )
    {
        setup();
    }
    relax_array();
    destroyArray(ARR1);
    destroyArray(ARR2);
    MPI_Finalize();
}

int main(int argc, char **argv)
{
    int c;

    while ((c = getopt (argc, argv, "d:p:")) != -1)
    {
        switch (c)
        {
            case 'd':
                DIM = atoi(optarg);
                break;
            case 'p':
                PRECISION = atof(optarg);
                break;
            default:
                exit(-1);
        }
    }

    start_processes();

    return 0;
}


