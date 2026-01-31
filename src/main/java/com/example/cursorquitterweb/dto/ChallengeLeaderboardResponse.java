package com.example.cursorquitterweb.dto;

import java.util.List;

/**
 * 戒色天数排行榜返回数据
 */
public class ChallengeLeaderboardResponse {

    private List<UserChallengeRankDto> list;

    public ChallengeLeaderboardResponse() {
    }

    public ChallengeLeaderboardResponse(List<UserChallengeRankDto> list) {
        this.list = list;
    }

    public List<UserChallengeRankDto> getList() {
        return list;
    }

    public void setList(List<UserChallengeRankDto> list) {
        this.list = list;
    }
}
