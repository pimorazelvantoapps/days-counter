# Glance records each receiver's widget class by name and finds placed widgets through that name.
# Were R8 to rename DaysCounterWidget between releases, updates after an app update would reach no
# widget until Glance records the receiver again.
-keepnames class com.pimorazelvanto.dayscounter.widget.DaysCounterWidget
