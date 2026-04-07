/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.jmh.results;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.util.ListStatistics;
import org.openjdk.jmh.util.Statistics;

/**
 * Result class that stores raw per-iteration ops and duration for CodSpeed walltime collection.
 *
 * <p>Behaves like {@link AverageTimeResult} for display purposes (shows avg time/op), but
 * retains the raw {@code (ops, durationNs)} pair so that {@code CodSpeedResultCollector} can
 * read them without floating-point back-calculation.
 */
public class CodSpeedResult extends Result<CodSpeedResult> {
  private static final long serialVersionUID = 1L;

  private final long rawOps;
  private final long rawDurationNs;

  public CodSpeedResult(
      ResultRole role, String label, double ops, long durationNs, TimeUnit tu) {
    this(
        role,
        label,
        of(durationNs / (ops * TimeUnit.NANOSECONDS.convert(1, tu))),
        TimeValue.tuToString(tu) + "/op",
        Math.round(ops),
        durationNs);
  }

  CodSpeedResult(
      ResultRole role,
      String label,
      Statistics value,
      String unit,
      long rawOps,
      long rawDurationNs) {
    super(role, label, value, unit, AggregationPolicy.AVG);
    this.rawOps = rawOps;
    this.rawDurationNs = rawDurationNs;
  }

  public long getRawOps() {
    return rawOps;
  }

  public long getRawDurationNs() {
    return rawDurationNs;
  }

  @Override
  protected Aggregator<CodSpeedResult> getThreadAggregator() {
    return new SummingAggregator();
  }

  @Override
  protected Aggregator<CodSpeedResult> getIterationAggregator() {
    return new SummingAggregator();
  }

  /**
   * Sums raw ops and durations across threads/iterations, recomputes the avg score.
   */
  static class SummingAggregator implements Aggregator<CodSpeedResult> {
    @Override
    public CodSpeedResult aggregate(Collection<CodSpeedResult> results) {
      long totalOps = 0;
      long totalDurationNs = 0;
      for (CodSpeedResult r : results) {
        totalOps += r.rawOps;
        totalDurationNs += r.rawDurationNs;
      }

      ListStatistics stat = new ListStatistics();
      for (CodSpeedResult r : results) {
        stat.addValue(r.getScore());
      }

      return new CodSpeedResult(
          AggregatorUtils.aggregateRoles(results),
          AggregatorUtils.aggregateLabels(results),
          stat,
          AggregatorUtils.aggregateUnits(results),
          totalOps,
          totalDurationNs);
    }
  }
}
