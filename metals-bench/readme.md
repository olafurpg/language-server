# Benchmarks

Date: 2018 October 8th, commit 59bda2ac81a497fa168677499bd1a9df60fec5ab
```
> bench/jmh:run -i 10 -wi 10 -f1 -t1
[info] Benchmark                   Mode  Cnt  Score   Error  Units
[info] MetalsBench.indexSources      ss   10  0.620 ± 0.058   s/op
[info] MetalsBench.javaMtags         ss   10  7.233 ± 0.017   s/op
[info] MetalsBench.scalaMtags        ss   10  4.617 ± 0.034   s/op
[info] MetalsBench.scalaToplevels    ss   10  0.361 ± 0.005   s/op
> bench/run
[info] info  elapsed: 3265ms
[info] info  java lines: 0
[info] info  scala lines: 1263569
[info] bench.Memory.printFootprint:11 iterable.source: "index"
[info] bench.Memory.printFootprint:12 Units.approx(size): "16.9M"
[info] bench.Memory.printFootprint:24 count: 12L
[info] bench.Memory.printFootprint:25 Units.approx(elementSize): "1.41M"
```


## `FuzzyBench`

```
[info] Benchmark                                  (query)  Mode  Cnt  Score   Error  Units
[info] FuzzyBench.upper                               FSM    ss   10  0.233 ± 0.058   s/op
[info] FuzzyBench.upper                             Actor    ss   10  0.333 ± 0.010   s/op
[info] FuzzyBench.upper                            Actor(    ss   10  0.033 ± 0.004   s/op
[info] FuzzyBench.upper                             FSMFB    ss   10  0.022 ± 0.003   s/op
[info] FuzzyBench.upper                            ActRef    ss   10  0.196 ± 0.016   s/op
[info] FuzzyBench.upper                          actorref    ss   10  0.270 ± 0.019   s/op
[info] FuzzyBench.upper                         actorrefs    ss   10  0.153 ± 0.003   s/op
[info] FuzzyBench.upper                        fsmbuilder    ss   10  0.292 ± 0.005   s/op
[info] FuzzyBench.upper                fsmfunctionbuilder    ss   10  0.136 ± 0.005   s/op
[info] FuzzyBench.upper  abcdefghijklmnopqrstabcdefghijkl    ss   10  0.203 ± 0.012   s/op
```

```
s.c.m.PriorityQueue
[info] Benchmark                   (query)  Mode  Cnt    Score    Error  Units
[info] ClasspathFuzzBench.run  InputStream    ss   30   63.669 ±  9.473  ms/op
[info] ClasspathFuzzBench.run          Str    ss   30  111.698 ± 10.954  ms/op
[info] ClasspathFuzzBench.run         Like    ss   30  111.270 ± 10.656  ms/op
[info] ClasspathFuzzBench.run          M.E    ss   30  286.268 ± 55.467  ms/op
[info] ClasspathFuzzBench.run         File    ss   30  656.876 ± 26.848  ms/op
[info] ClasspathFuzzBench.run        Files    ss   30  166.028 ± 10.479  ms/op

j.u.PriorityQueue
[info] ClasspathFuzzBench.run  InputStream    ss   30   34.366 ±  0.963  ms/op
[info] ClasspathFuzzBench.run          Str    ss   30   54.419 ±  2.161  ms/op
[info] ClasspathFuzzBench.run         Like    ss   30   20.507 ±  2.058  ms/op
[info] ClasspathFuzzBench.run          M.E    ss   30  149.336 ± 23.665  ms/op
[info] ClasspathFuzzBench.run         File    ss   30   83.704 ± 17.367  ms/op
[info] ClasspathFuzzBench.run        Files    ss   30   31.744 ±  1.423  ms/op
```
