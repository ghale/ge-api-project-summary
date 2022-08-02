# Gradle Enterprise API Project Summary

This repository will use the Gradle Enterprise API to gather a summary for each project that has a build scan in a given Gradle Enterprise instance
## How to build

Execute:

```
$ ./gradlew install
```

This builds and installs the program into `build/install/ge-api-project-summary`.
You can use the `build/install/ge-api-project-summary/bin/ge-api-project-summary` script to run the program.

## How to run

A Gradle Enterprise access key with the “Export build data via the API” permission is required.

To create an access key:

1. Sign in to Gradle Enterprise.
2. Access "My settings" from the user menu in the top right-hand corner of the page.
3. Access "Access keys" from the left-hand menu.
4. Click "Generate" on the right-hand side and copy the generated access key.

The access key should be saved to a file, which will be supplied as a parameter to the program.

Next, execute:

```
$ build/install/ge-api-project-summary/bin/ge-api-project-summary --server-url=«serverUrl» --access-key-file=«accessKeyFile» --days 1 --format-duration --work-units 40
```

Options

- `«server-url»` (required): The address of your Gradle Enterprise server (e.g. `https://ge.example.com`) [**required**]
- `«access-key-file»` (required): The path to the file containing the access key [**required**]
- `«days»`: The number of days in the past it should start processing. This option takes precedence over `«hours»`. If no `«hours»` or `«days»` is specified then the default is 1 day.
- `«hours»`: The number of hours in the past it should start processing
- `«format-duration»`: Specifying this option will format the duration in the output by `HH:mm:ss`
- `«concurrency»` (default: 32): The number of threads used for processing the build _[default: 32]_
- `«work-units»` (default: 10): The number of top tasks/goals it should display per project _[default: 10]_
- `«project-name»` (optional): The name of the project to limit reporting to (reports all builds when omitted)

The program will first fetch the entire list of builds it should process and then process them in parallel.
While fetching the builds it will print `Fetching |  1500 builds queued | Currently processing: 2022-06-01 13:06`.
While processing the builds it will print `Processing |    1944/2080 builds |   0 errors |   51 builds/sec | Currently processing: 2022-06-02 15:53 | Progress: 91% | 00:00:38 elapsed | 00:00:03 ETA`

The output will be written to 2 separate files, one for Gradle `gradle_summary_[date].json` and one for Maven `maven_summary_[date].json`.
