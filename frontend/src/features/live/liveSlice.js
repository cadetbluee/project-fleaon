// src/features/live/liveSlice.js
import { createSlice } from '@reduxjs/toolkit';
import { fetchCategories, createLiveBroadcast } from './actions';

const liveSlice = createSlice({
  name: 'live',
  initialState: {
    categories: [],
    liveBroadcast: null,
    status: 'idle',
    error: null,
  },
  reducers: {},
  extraReducers: (builder) => {
    builder
    //   .addCase(fetchCategories.fulfilled, (state, action) => {
    //     state.categories = action.payload;
    //   }) //카테고리 가져오는 api나오면 주석풀기
      .addCase(createLiveBroadcast.pending, (state) => {
        state.status = 'loading';
      })
      .addCase(createLiveBroadcast.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.liveBroadcast = action.payload;
      })
      .addCase(createLiveBroadcast.rejected, (state, action) => {
        state.status = 'failed';
        state.error = action.error.message;
      });
  },
});

export default liveSlice.reducer;