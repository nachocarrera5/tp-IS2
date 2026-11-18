document.querySelectorAll("[data-attribute-position-filter]").forEach((positionSelect) => {
    const form = positionSelect.closest("form");
    const options = form.querySelectorAll("[data-attribute-option]");

    const updateOptions = () => {
        const goalkeeper = positionSelect.value === "GOALKEEPER";
        options.forEach((option) => {
            const goalkeeperOnly = option.dataset.goalkeeperOnly === "true";
            const goalkeeperShared = option.dataset.goalkeeperShared === "true";
            const available = goalkeeper
                ? goalkeeperOnly || goalkeeperShared
                : !goalkeeperOnly;

            option.hidden = !available;
            option.querySelectorAll("input").forEach((input) => {
                input.disabled = !available;
            });
            if (!available) {
                const selection = option.querySelector("[data-attribute-selection]");
                if (selection) selection.checked = false;
            }
        });
    };

    positionSelect.addEventListener("change", updateOptions);
    updateOptions();
});
