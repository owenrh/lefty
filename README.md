```
all ur bytes r belong to us ...
   __       __ _        ____
  / /  ___ / _| |_ _   _\ \ \
 / /  / _ \ |_| __| | | |\ \ \
/ /__|  __/  _| |_| |_| |/ / /
\____/\___|_|  \__|\__, /_/_/
                   |___/
```

### Overview
Lefty is a proof-of-concept that applies brute force search in combination with mechanical sympathy to provide search performance which beats algorithmic approaches in certain niches - most notably similarity search.

I developed this as a side project back in 2016. It is implemented in Java, using off-heap memory, mixed in with a bit of Springboot. 

### Performance
Lefty can perform a hamming distance search over 1 billion 64-bit hashes on my laptop (think mid-2012 MBP) in around 675 ms. (Note, prior to the Meltdown and Spectre patching this was < 400 ms). On a chunky AWS instance, this could be down under 100 ms. 

As a comparison, folk were getting around the 400 ms using algorithmic approaches when searching over 100M 32-bit hashes, so 20 times less data - see this Stackoverflow thread for more info - https://stackoverflow.com/questions/6389841/efficiently-find-binary-strings-with-low-hamming-distance-in-large-set/29474234

### Limitations
Lefty exhibits linear vertical scalability and could easily be adapted to provide linear horizontal scalability.

However, Lefty is only a PoC and not production ready. Given the prevalence of higher memory GPUs the approach has aged somewhat and a GPU-based approach would almost certainly deliver better performance.

### Running Lefty
To run the Lefty hamming code example you will need to do the following:

1. Download and build the Lefty hamming code project.
1. Generate some test data.
1. Run up Lefty.
1. Fire some queries at it.

#### Lefty Hamming Distance
You will need to download the following two projects:
* https://github.com/owenrh/lefty-api
* https://github.com/owenrh/lefty-hamming-distance

Then build and `mvn install` the API project. This will allow you to build the hamming distance project.

#### Test data generation
Lefty contains a test generator which can be fired up from the Lefty root directory as follows ...

First setup your environment, to locate the place where you want to store the generated data:

```
export LEFTY_DATA_DIR=/your/path/to/data
```

then run the generator:
```
java -Dspring.profiles.active=generator -Xmx1g -Xms1g -cp target/uber-lefty.jar com.dataflow.lefty.Generator <insert number of records here>
```

Things to keep in mind at this point:
* you need enough diskspace for the data generated, e.g. 1B 64 bit hashes are 8G of data.
* you need enough RAM to keep the data in for searching; Lefty relies on being able to hold all queried data in memory.
* generating GBs of data can take a while ; )

#### Running Lefty
First, setup the necessary enviro variables setup:
```
export LEFTY_DATA_DIR=/your/path/to/data
export LEFTY_JAVA_OPTS='-Xmx32m -Xms32m'
export LEFTY_JOB_DIR=/your/path/to/lefty-hamming-distance/target
```

Execute the run script from the root directory as follows:
```
./bin/lefty
```

At this point, you will need to wait for the data files to be loaded into memory. Lefty will inform you once the data load is complete.

#### Querying Lefty
You should now be able to hit Lefty with a REST request of the following form:
```
curl -d '{ "maxDistance": "11", "comparisonHash": "4164436981991581735" }' -H 'Content-Type: application/json' http://localhost:8080/compute
```

