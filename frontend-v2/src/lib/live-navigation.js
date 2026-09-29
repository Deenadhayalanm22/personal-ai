// FIN-EPIC-007: stable live Journey URLs, independent of the sample's bounded dates.
import { isDate } from './live-read.js';
const monthPattern=/^\d{4}-(0[1-9]|1[0-2])$/;
const views=new Set(['journey','money','conversation']);

export function readLiveLocation(search, fallbackMonth) {
  const query=new URLSearchParams(search);
  const requestedMonth=query.get('month');
  const month=monthPattern.test(requestedMonth || '')?requestedMonth:fallbackMonth;
  const requestedDay=query.get('day');
  const day=isDate(requestedDay) && requestedDay.slice(0,7)===month?requestedDay:null;
  const requestedView=query.get('view');
  const view=views.has(requestedView)?requestedView:'journey';
  const requestedStory=query.get('story');
  const story=view==='journey' && requestedStory && requestedStory.length<=128?requestedStory:null;
  return {month,day,view,story};
}

export function liveUrl({month,day=null,view='journey',story=null}) {
  if(!monthPattern.test(month || '')) throw new TypeError('Invalid month');
  const query=new URLSearchParams({month});
  if(day)query.set('day',day);
  if(view!=='journey')query.set('view',view);
  if(story && view==='journey')query.set('story',story);
  return `/?${query}`;
}
