package com.kidozh.discuzhub.activities.ui.FavoriteThread;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import com.kidozh.discuzhub.R;
import com.kidozh.discuzhub.daos.FavoriteThreadDao;
import com.kidozh.discuzhub.database.FavoriteThreadDatabase;
import com.kidozh.discuzhub.entities.FavoriteThread;
import com.kidozh.discuzhub.entities.bbsInformation;
import com.kidozh.discuzhub.entities.forumUserBriefInfo;
import com.kidozh.discuzhub.results.FavoriteThreadResult;
import com.kidozh.discuzhub.services.DiscuzApiService;
import com.kidozh.discuzhub.utilities.bbsConstUtils;
import com.kidozh.discuzhub.utilities.networkUtils;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FavoriteThreadViewModel extends AndroidViewModel {

    private static final String TAG = FavoriteThreadViewModel.class.getSimpleName();
    public MutableLiveData<Integer> networkState = new MutableLiveData<>(bbsConstUtils.NETWORK_STATUS_SUCCESSFULLY);
    public MutableLiveData<String> errorMsgKey = new MutableLiveData<>(""),
            errorMsgContent = new MutableLiveData<>("");
    private LiveData<PagedList<FavoriteThread>> favoriteThreadListData;
    @NonNull
    public MutableLiveData<Integer> totalCount = new MutableLiveData<>(-1);
    @NonNull
    public MutableLiveData<List<FavoriteThread>> favoriteThreadInServer = new MutableLiveData<>(new ArrayList<>());
    public MutableLiveData<List<FavoriteThread>> newFavoriteThread = new MutableLiveData<>(new ArrayList<>());
    private OkHttpClient client = new OkHttpClient();
    bbsInformation bbsInfo;
    forumUserBriefInfo userBriefInfo;

    FavoriteThreadDao dao;

    PagedList.Config myPagingConfig = new PagedList.Config.Builder()
            .setEnablePlaceholders(true)
            .setPageSize(5)
            .build();

    public FavoriteThreadViewModel(@NonNull Application application) {
        super(application);
        dao = FavoriteThreadDatabase.getInstance(application).getDao();
    }

    public void setInfo( @NonNull bbsInformation bbsInfo, forumUserBriefInfo userBriefInfo) {

        this.bbsInfo = bbsInfo;
        this.userBriefInfo = userBriefInfo;
        client = networkUtils.getPreferredClientWithCookieJarByUser(getApplication(),userBriefInfo);
        favoriteThreadListData = new LivePagedListBuilder<>(dao.getFavoriteThreadPageListByBBSId(bbsInfo.getId(),userBriefInfo.getUid()),myPagingConfig).build();
    }

    public LiveData<PagedList<FavoriteThread>> getFavoriteThreadListData() {
        return favoriteThreadListData;
    }

    public void startSyncFavoriteThread(){
        getFavoriteThread(1);
    }



    private void getFavoriteThread(int page){
        networkState.postValue(bbsConstUtils.NETWORK_STATUS_LOADING);
        Retrofit retrofit = networkUtils.getRetrofitInstance(bbsInfo.base_url,client);
        DiscuzApiService apiService = retrofit.create(DiscuzApiService.class);
        Call<FavoriteThreadResult> favoriteCall = apiService.getFavoriteThreadResult(page);
        favoriteCall.enqueue(new Callback<FavoriteThreadResult>() {
            @Override
            public void onResponse(Call<FavoriteThreadResult> call, Response<FavoriteThreadResult> response) {
                if(response.isSuccessful() && response.body()!=null){
                    FavoriteThreadResult result = response.body();
                    if(result.isError()){
                        networkState.postValue(bbsConstUtils.NETWORK_STATUS_FAILED);
                        errorMsgKey.postValue(result.message.key);
                        errorMsgContent.postValue(getApplication().getString(R.string.discuz_error,result.message.key,result.message.content));
                    }
                    else {
                        totalCount.postValue(result.favoriteThreadVariable.count);
                        newFavoriteThread.postValue(result.favoriteThreadVariable.favoriteThreadList);
                        List<FavoriteThread> curFavoriteThreadList =
                                favoriteThreadInServer.getValue() == null ? new ArrayList<>()
                                : favoriteThreadInServer.getValue();
                        curFavoriteThreadList.addAll(result.favoriteThreadVariable.favoriteThreadList);
                        favoriteThreadInServer.postValue(curFavoriteThreadList);

                        // recursive
                        if(result.favoriteThreadVariable.count > curFavoriteThreadList.size()){
                            getFavoriteThread(page+1);
                        }
                    }
                }
                else {
                    networkState.postValue(bbsConstUtils.NETWORK_STATUS_FAILED);
                    errorMsgContent.postValue(getApplication().getString(R.string.network_failed));
                }
            }

            @Override
            public void onFailure(Call<FavoriteThreadResult> call, Throwable t) {
                networkState.postValue(bbsConstUtils.NETWORK_STATUS_FAILED);
                errorMsgContent.postValue(getApplication().getString(R.string.network_failed));
            }
        });
    }
}
