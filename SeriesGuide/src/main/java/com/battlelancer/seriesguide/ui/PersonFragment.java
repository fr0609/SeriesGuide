/*
 * Copyright 2014 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.battlelancer.seriesguide.ui;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.loaders.PersonLoader;
import com.battlelancer.seriesguide.util.ServiceUtils;
import com.battlelancer.seriesguide.util.TmdbTools;
import com.uwetrottmann.tmdb.entities.Person;

/**
 * Displays details about a crew or cast member and their work.
 */
public class PersonFragment extends Fragment {

    @InjectView(R.id.progressBarPerson) ProgressBar mProgressBar;

    @InjectView(R.id.imageViewPersonHeadshot) ImageView mImageHeadshot;
    @InjectView(R.id.textViewPersonName) TextView mTextName;
    @InjectView(R.id.textViewPersonBiography) TextView mTextBiography;

    public static PersonFragment newInstance(int tmdbId) {
        PersonFragment f = new PersonFragment();

        Bundle args = new Bundle();
        args.putInt(InitBundle.TMDB_ID, tmdbId);
        f.setArguments(args);

        return f;
    }

    public interface InitBundle {
        String TMDB_ID = "tmdb_id";
    }

    public PersonFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_person, container, false);
        ButterKnife.inject(this, rootView);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getLoaderManager().initLoader(PeopleActivity.PERSON_LOADER_ID, null,
                mPersonLoaderCallbacks);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        ButterKnife.reset(this);
    }

    private void populatePersonViews(Person person) {
        if (person == null) {
            // TODO display error message
            return;
        }

        mTextName.setText(person.name);
        mTextBiography.setText(person.biography);

        if (!TextUtils.isEmpty(person.profile_path)) {
            ServiceUtils.getPicasso(getActivity())
                    .load(TmdbTools.buildProfileImageUrl(getActivity(), person.profile_path,
                            TmdbTools.ProfileImageSize.H632))
                    .placeholder(new ColorDrawable(getResources().getColor(R.color.protection_dark)))
                    .into(mImageHeadshot);
        }
    }

    /**
     * Shows or hides a custom indeterminate progress indicator inside this activity layout.
     */
    private void setProgressVisibility(boolean isVisible) {
        if (mProgressBar.getVisibility() == (isVisible ? View.VISIBLE : View.GONE)) {
            // already in desired state, avoid replaying animation
            return;
        }
        mProgressBar.startAnimation(AnimationUtils.loadAnimation(mProgressBar.getContext(),
                isVisible ? R.anim.fade_in : R.anim.fade_out));
        mProgressBar.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private LoaderManager.LoaderCallbacks<Person> mPersonLoaderCallbacks
            = new LoaderManager.LoaderCallbacks<Person>() {
        @Override
        public Loader<Person> onCreateLoader(int id, Bundle args) {
            setProgressVisibility(true);

            int tmdbId = getArguments().getInt(InitBundle.TMDB_ID);
            return new PersonLoader(getActivity(), tmdbId);
        }

        @Override
        public void onLoadFinished(Loader<Person> loader, Person data) {
            setProgressVisibility(false);
            if (isAdded()) {
                populatePersonViews(data);
            }
        }

        @Override
        public void onLoaderReset(Loader<Person> loader) {
            // do nothing, preferring stale data over no data
        }
    };
}
