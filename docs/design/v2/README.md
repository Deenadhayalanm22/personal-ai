# Money Stories — UI v2

Current direction: [revised 15-stage implementation plan](implementation-plan.md). The earlier landscape boards below are archived explorations, not instructions to build a city. The working stage 1 preview now uses a compact date journey, a factual summary, evidence and at most one small supporting illustration. A quiet Tell us entry now offers editable, explicitly confirmed sample expenses for the selected day; it uses a limited local demo parser, not AI, and resets on reload. Navigation is Journey / Money. Expanded scenery and Easter eggs are deferred.

Status: concept review. These sketches are proposals, not accepted feature specifications or production UI. All amounts are fictional examples.

This folder isolates the new journey exploration from the existing UI. No application routes, components, contracts, or active epics are changed by these artifacts. Implementation begins only after flows are settled and the relevant active epics and contracts are updated.

## Review boards

1. [Daily journey](sketches/01-daily-journey.png): 01 Today, 02 Busy day, 03 Quiet day, 04 Jump to date.
2. [Story history](sketches/02-story-history.png): 05 Older chapter, 06 Same-day updates, 07 Late entry, 08 Story and evidence/correction.
3. [Commitments and tools](sketches/03-commitments-and-tools.png): 09 Due commitment, 10 Commitment details, 11 Upcoming stop, 12 Money and capture.
4. [Living landscape — actions](sketches/04-living-landscape-actions.png): 13 Loan repayment bridge, 14 Investment sapling, 15 Everyday spending landmarks, 16 Savings shelter.
5. [Living landscape — time](sketches/05-living-landscape-time.png): 17 Original sapling, 18 Growing tree, 19 Completed loan bridge, 20 Month-end landscape.

## Living landscape exploration

Boards 04–05 extend the first sketches with a larger landscape above the readable day card. They are an alternative composition for review, not a replacement of accepted flows. Original boards remain intact. Exact built-in image-generation prompts are in [landscape-prompts.json](landscape-prompts.json).

- Confirmed loan payments add bridge milestones. A completed bridge requires verified loan closure; proportional progress requires reliable repayment data, not an invented percentage.
- Investment trees reflect recorded contributions, never market performance. Bird and butterfly visits are optional small milestone effects.
- Ordinary recorded expenses create contextual landmarks. Missing activity or increased spending must not cause dead trees, punishment, or unhappy travellers. Decorative ambient scenery is distinct from labelled financial landmarks.
- Savings shelter progress represents money recorded as set aside for a payment; it does not imply the underlying bill is paid.
- Returning to an earlier date restores its historical landscape and published chapter. Corrections and late entries retain the explicit history rules above; a late entry can add a labelled footprint on its effective date.
- Month-end view connects the landmarks and opens the narrative summary. Tapping landmarks offers the same evidence and history available through text controls.
- Animation is brief, optional, and compatible with reduced motion. Every meaningful event also has a readable card; scenery is not the sole carrier of financial information.

### Landscape sketch limitations for the next pass

The generated boards omit Today/Jump to date controls; preserve them in the actual prototype. Date markers must move in daily increments unless a larger interval is explicitly selected (board 05 illustrates spaced milestones). Screens labelled Today are separate illustrative dates. Screen 16's illustrated progress bar should be exactly 50% for ₹10,000 of ₹20,000. Decorative slogans added by image generation are not approved product copy. Bridge geometry and traveller placement need consistency across dates before animation work.

## Proposed interaction rules

- Open on today. Horizontal movement on the road selects a day; vertical scrolling reads that day. Longer stories open a reader. Date picker and Today avoid long sequential navigation.
- Activities, changed totals, published stories, and planning reminders retain distinct labels. Recorded spending is not a bank balance.
- Preserve published historical narratives and their evidence. Show corrections and later entries explicitly. Payment date and recording date are separate.
- Group same-day versions. Do not invent insights or interpret missing records as zero spending.
- Future stops contain labelled plans, not completed activity. Management details return to the original selected date.
- Journey and Money are primary destinations; profile is reached through the avatar. Capture uses the existing WhatsApp handoff, with selected past-date context where applicable.
- The road must also support tap/keyboard date selection and reduced motion. Swiping cannot be the only way to move.
- News and suggestions are deferred. Loading, offline, errors, and first-use states need a later design pass before implementation.

## Review questions

Reference a screen number when giving feedback. Does the road feel useful? Is it clear which date is selected? Can you distinguish recorded activity, a published story, and a future plan? Is history versus corrected information clear? Can you reach and return from management tools easily?

## Sketch review notes

- The three boards explore the same flow but differ slightly in road treatment and button placement; final design must standardize these. Today and Jump to date must be reachable on every journey screen even where omitted in the image.
- The September 2026 calendar illustration has a weekday alignment error: September 1 falls on Tuesday. Use a real date component for a prototype.
- Screen 06 depicts Earlier updates expanded; its default should be collapsed.
- The traveller must sit on the selected date. Screen 11's traveller placement is illustrative and needs correction in the final design.
- Designer notes such as “Return to the same journey date” belong outside the production interface. Screen 12's WhatsApp callout describes the handoff, rather than proposed permanent UI copy.
- Scenarios are independent examples, not a single reconciled transaction fixture. Screen 08's corrected total refers to the historical September 10 scope, not the September 15 current total.

Generated with the built-in image generation tool. Exact prompts are saved in prompts.json. Image lettering is illustrative; these notes define the proposed semantics where a sketch is ambiguous.
