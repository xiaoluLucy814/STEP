// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps;

import com.google.sps.Event;
import com.google.sps.TimeRange;
import com.google.sps.MeetingRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class FindMeetingQuery {
  public Collection<TimeRange> query(Collection<Event> events, 
                                     MeetingRequest request) {
    // Get unavailable time for all required attendees in meeting request
    ArrayList<TimeRange> unavailable = new ArrayList<>();
    for (String attendee : request.getAttendees()) {
      ArrayList<TimeRange> meetingTimes = getMeetingTimes(events, attendee);
      merge(unavailable, meetingTimes);
    }

    // Get time ranges that are longer than the requested meeting duration
    Collection<TimeRange> result = getAvailability(unavailable);
    result.removeIf( 
      timeRange -> timeRange.duration() < request.getDuration());

    return result;
  }

  /**
   * Adds the unavailable time ranges of a new attendee to the cumulative
   * unavailable time ranges.
   * Resolves conflicts by merging overlapping ranges into new ranges.
   *
   * @param result Cumulative unavailable time ranges
   * @param other Unavailable time ranges of a new attendee
   */
  private void merge(ArrayList<TimeRange> result, ArrayList<TimeRange> other) {
    if (result.isEmpty()) {
      result.addAll(other);
      return;
    }
    if (other.isEmpty()) {
      return;
    }

    result.addAll(other);
    Collections.sort(result, TimeRange.ORDER_BY_START);

    // Merge overlapping ranges
    for (int i = 0; i < result.size()-1; i++) {
      TimeRange current = result.get(i);
      TimeRange next = result.get(i+1);

      if (current.overlaps(next)) {
        if (current.contains(next)) {
          // Case 1:  |_______|
          //            |__|
          result.remove(next);
        } else {
          // Case 2:  |______|
          //               |______|
          result.set(i, TimeRange.fromStartEnd(current.start(), 
                                               next.end(), false));
          result.remove(next);
          i--;
        }
      }
    }
  }

  /** Gets an attendee's unavailable time ranges */
  private ArrayList<TimeRange> getMeetingTimes(Collection<Event> events, 
                                               String attendee) {
    ArrayList<TimeRange> meetingTimes = new ArrayList<>();
    for (Event e : events) {
      if (e.getAttendees().contains(attendee)) {
        meetingTimes.add(e.getWhen());
      }
    }

    return meetingTimes;
  }

  /**
   * @param sortedUnavailable Attendees' unavailable time ranges sorted 
   *                          by start time in ascending order
   * @return Attendees' available time ranges sorted by start time in 
   *         ascending order
   */
  private Collection<TimeRange> getAvailability(Collection<TimeRange> sortedUnavailable) {
    Collection<TimeRange> availability = new ArrayList<>();
    int point = TimeRange.START_OF_DAY;
    for (TimeRange t : sortedUnavailable) {
      availability.add(TimeRange.fromStartEnd(point, t.start(), false));
      point = t.end();
    }
    availability.add(TimeRange.fromStartEnd(point, TimeRange.END_OF_DAY, true));

    return availability;
  }
}